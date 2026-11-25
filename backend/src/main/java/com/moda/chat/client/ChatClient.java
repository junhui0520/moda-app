package com.moda.chat.client;

import com.moda.chat.model.ChatContext;
import com.moda.common.enums.ChatRole;
import java.util.List;

/**
 * 챗봇 LLM 어댑터. {@code moda.chat.mode}로 구현체를 선택합니다 ({@link ChatClientConfig}).
 * <ul>
 *   <li>{@code mock}: {@link MockChatClient} — LLM 키 없이 세션 컨텍스트·질문 키워드 기반 템플릿 답변</li>
 *   <li>{@code external}: {@link ExternalLlmChatClient} — OpenAI 호환 Chat Completions API</li>
 * </ul>
 * 오류는 {@link com.moda.global.error.BusinessException}으로 던집니다:
 * {@code 503 CHAT_SERVER_UNAVAILABLE}(연결 실패·429·5xx·인증 오류·빈 답변) / {@code 504 CHAT_TIMEOUT}.
 */
public interface ChatClient {

    ChatReply complete(ChatCompletionCommand command);

    /**
     * @param systemPrompt 세션 컨텍스트 + 가드레일 ({@link com.moda.chat.service.ChatPromptBuilder})
     * @param history      이전 대화 (오래된 순, 최대 19턴)
     * @param userMessage  이번 사용자 메시지 (trim 적용)
     * @param context      구조화된 세션 컨텍스트 (Mock 답변 생성용, External은 사용하지 않음)
     */
    record ChatCompletionCommand(String systemPrompt, List<ChatTurn> history, String userMessage, ChatContext context,
                                 ChatOptions options) {
    }

    record ChatTurn(ChatRole role, String content) {
    }

    /** @param mockDelayMs {@code X-Mock-Delay-Ms} 원본값 ({@code moda.chat.mode=mock}에서만 사용) */
    record ChatOptions(String mockDelayMs) {

        public static ChatOptions none() {
            return new ChatOptions(null);
        }
    }

    record ChatReply(String content, String model) {
    }
}
