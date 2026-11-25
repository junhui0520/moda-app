package com.moda.chat.service;

import com.moda.chat.client.ChatClient;
import com.moda.chat.client.ChatClient.ChatCompletionCommand;
import com.moda.chat.client.ChatClient.ChatOptions;
import com.moda.chat.client.ChatClient.ChatReply;
import com.moda.chat.client.ChatClient.ChatTurn;
import com.moda.chat.domain.ChatMessage;
import com.moda.chat.dto.ChatDtos.ChatHistoryData;
import com.moda.chat.dto.ChatDtos.ChatMessageDto;
import com.moda.chat.dto.ChatDtos.ChatSendData;
import com.moda.chat.model.ChatContext;
import com.moda.chat.repository.ChatMessageRepository;
import com.moda.common.enums.ChatRole;
import com.moda.global.config.ModaProperties;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import com.moda.global.time.KstTime;
import com.moda.session.domain.GuestSession;
import com.moda.session.service.GuestSessionService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 챗봇 API ⑨·⑩ (docs/api-spec.md v1.2).
 * <ul>
 *   <li>선행 조건: ⑤와 동일 (기본 정보 → {@code 409 PROFILE_INPUT_REQUIRED}, 얼굴·체형 → {@code 409 ANALYSIS_INCOMPLETE})</li>
 *   <li>1턴 = USER + ASSISTANT 저장 성공 쌍, 세션당 최대 {@code moda.chat.max-turns}(20)턴</li>
 *   <li><b>LLM 호출이 성공했을 때만</b> 메시지 쌍 저장·턴 차감·세션 TTL 연장. 503/504 실패 시 아무것도 저장하지 않음</li>
 *   <li>LLM 호출(최대 20초)은 DB 트랜잭션 밖에서 수행. 같은 세션 요청은 {@link ChatSessionLocks}로 순차 처리</li>
 * </ul>
 */
@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final GuestSessionService sessionService;
    private final ChatContextAssembler contextAssembler;
    private final ChatPromptBuilder promptBuilder;
    private final ChatTemplates templates;
    private final ChatReplySanitizer sanitizer;
    private final ChatClient chatClient;
    private final ChatSessionLocks locks;
    private final Clock clock;
    private final int maxTurns;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnlyTx;

    public ChatService(ChatMessageRepository repository, GuestSessionService sessionService, ChatContextAssembler contextAssembler,
                       ChatPromptBuilder promptBuilder, ChatTemplates templates, ChatReplySanitizer sanitizer, ChatClient chatClient,
                       ChatSessionLocks locks, Clock clock, ModaProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.sessionService = sessionService;
        this.contextAssembler = contextAssembler;
        this.promptBuilder = promptBuilder;
        this.templates = templates;
        this.sanitizer = sanitizer;
        this.chatClient = chatClient;
        this.locks = locks;
        this.clock = clock;
        this.maxTurns = properties.chat().maxTurns();
        this.tx = new TransactionTemplate(transactionManager);
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------ ⑨ 대화 조회 (화면 진입)

    /** 조회 API이므로 세션 TTL을 연장하지 않습니다. */
    public ChatHistoryData getMessages(String sessionId) {
        return readOnlyTx.execute(status -> {
            GuestSession session = requireChatReady(sessionId);
            List<ChatMessage> history = repository.findHistory(sessionId);
            int remaining = remaining(history);
            ChatContext ctx = contextAssembler.build(session);
            List<String> prompts = remaining <= 0 ? List.of()
                    : history.isEmpty() ? templates.initialPrompts(ctx)
                    : templates.followUpPrompts(ctx, lastUserMessage(history), userMessages(history));
            return new ChatHistoryData(sessionId, templates.welcome(ctx), history.stream().map(ChatService::toDto).toList(),
                    prompts, remaining, maxTurns, KstTime.toOffset(session.getExpiresAt()));
        });
    }

    // ------------------------------------------------------------------ ⑩ 메시지 전송

    public ChatSendData send(String sessionId, String rawMessage, ChatOptions options) {
        String message = rawMessage.strip();
        ReentrantLock lock = locks.forSession(sessionId);
        lock.lock();
        try {
            Prepared prepared = readOnlyTx.execute(status -> prepare(sessionId));
            Instant userAt = now();
            ChatReply reply = chatClient.complete(new ChatCompletionCommand(prepared.systemPrompt(), prepared.history(), message,
                    prepared.context(), options));                                   // 실패 시 예외 → 저장·턴 차감·TTL 연장 없음
            String content = sanitizer.sanitize(reply.content(), prepared.context());
            return tx.execute(status -> persist(sessionId, message, content, userAt, prepared.context()));
        } finally {
            lock.unlock();
        }
    }

    private record Prepared(ChatContext context, String systemPrompt, List<ChatTurn> history) {
    }

    /** 검증·턴 한도 확인 후, DB 최신 값으로 컨텍스트·시스템 프롬프트·이전 대화를 준비합니다. */
    private Prepared prepare(String sessionId) {
        GuestSession session = requireChatReady(sessionId);
        List<ChatMessage> history = repository.findHistory(sessionId);
        requireTurnAvailable(usedTurns(history));
        ChatContext ctx = contextAssembler.build(session);
        List<ChatTurn> turns = history.stream().map(m -> new ChatTurn(m.getRole(), m.getContent())).toList();
        return new Prepared(ctx, promptBuilder.build(ctx), turns);
    }

    /** LLM 성공 후에만 호출: 세션 재확인(호출 중 삭제·만료 대비) → 턴 재확인 → USER·ASSISTANT 저장 → TTL 연장. */
    private ChatSendData persist(String sessionId, String message, String reply, Instant userAt, ChatContext ctx) {
        GuestSession session = sessionService.requireActive(sessionId);
        int used = (int) repository.countByRole(sessionId, ChatRole.ASSISTANT);
        requireTurnAvailable(used);
        Instant now = now();
        int seq = used * 2;
        ChatMessage user = new ChatMessage(UUID.randomUUID().toString(), session, seq + 1, ChatRole.USER, message, userAt);
        ChatMessage assistant = new ChatMessage(UUID.randomUUID().toString(), session, seq + 2, ChatRole.ASSISTANT,
                truncate(reply), now);
        repository.save(user);
        repository.save(assistant);
        sessionService.extendExpiry(session, now);

        int remaining = maxTurns - (used + 1);
        List<String> prompts = remaining <= 0 ? List.of()
                : templates.followUpPrompts(ctx, message, userMessages(repository.findHistory(sessionId)));
        return new ChatSendData(sessionId, toDto(user), toDto(assistant), prompts, remaining, maxTurns,
                KstTime.toOffset(session.getExpiresAt()));
    }

    // ------------------------------------------------------------------ helpers

    private GuestSession requireChatReady(String sessionId) {
        GuestSession session = sessionService.requireActive(sessionId);              // 404 / 410
        sessionService.requireResultReady(session);                                  // 409 PROFILE_INPUT_REQUIRED → 409 ANALYSIS_INCOMPLETE
        return session;
    }

    private void requireTurnAvailable(int usedTurns) {
        if (usedTurns >= maxTurns) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("maxTurns", maxTurns);
            meta.put("usedTurns", usedTurns);
            meta.put("remainingTurns", 0);
            throw new BusinessException(ErrorCode.CHAT_TURN_LIMIT_EXCEEDED,
                    "이번 체험에서 대화할 수 있는 횟수(" + maxTurns + "회)를 모두 사용했어요.", List.of(), meta, null);
        }
    }

    private int remaining(List<ChatMessage> history) {
        return Math.max(0, maxTurns - usedTurns(history));
    }

    private static int usedTurns(List<ChatMessage> history) {
        return (int) history.stream().filter(m -> m.getRole() == ChatRole.ASSISTANT).count();
    }

    private static String lastUserMessage(List<ChatMessage> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == ChatRole.USER) {
                return history.get(i).getContent();
            }
        }
        return null;
    }

    private static List<String> userMessages(List<ChatMessage> history) {
        return history.stream().filter(m -> m.getRole() == ChatRole.USER).map(ChatMessage::getContent).toList();
    }

    private static String truncate(String content) {
        return content.length() <= ChatMessage.MAX_CONTENT_LENGTH ? content : content.substring(0, ChatMessage.MAX_CONTENT_LENGTH);
    }

    private static ChatMessageDto toDto(ChatMessage m) {
        return new ChatMessageDto(m.getMessageId(), m.getRole(), m.getContent(), KstTime.toOffset(m.getCreatedAt()));
    }

    private Instant now() {
        return KstTime.now(clock);
    }
}
