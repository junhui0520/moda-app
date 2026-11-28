package com.moda.chat.dto;

import com.moda.common.enums.ChatRole;
import com.moda.global.validation.TrimmedLength;
import java.time.OffsetDateTime;
import java.util.List;

/** 챗봇 API ⑨·⑩ 요청·응답 (docs/api-spec.md v1.2, 부록 B). */
public final class ChatDtos {

    public static final int MESSAGE_MAX_LENGTH = 500;

    private ChatDtos() {
    }

    /** ⑩ 요청: trim 후 1~500자, 공백·줄바꿈만 있는 문자열 불가. */
    public record ChatSendRequest(
            @TrimmedLength(min = 1, max = MESSAGE_MAX_LENGTH, message = "공백을 제외하고 1자 이상 500자 이하로 입력해 주세요.")
            String message) {
    }

    /** {@code ChatMessage}. {@code images}: 답변과 함께 보여 줄 스타일 이미지 (USER·이미지 없음은 []). */
    public record ChatMessageDto(String messageId, ChatRole role, String content, List<ChatImage> images, OffsetDateTime createdAt) {
    }

    /** {@code imageKey}: 결과 화면 추천과 같은 catalog 키 (앱이 이미지로 매핑), {@code label}: 스타일 이름. */
    public record ChatImage(String imageKey, String label) {
    }

    /** ⑨ 응답 */
    public record ChatHistoryData(
            String sessionId,
            String welcomeMessage,
            List<ChatMessageDto> messages,
            List<String> suggestedPrompts,
            int remainingTurns,
            int maxTurns,
            OffsetDateTime expiresAt) {
    }

    /** ⑩ 응답 */
    public record ChatSendData(
            String sessionId,
            ChatMessageDto userMessage,
            ChatMessageDto assistantMessage,
            List<String> suggestedPrompts,
            int remainingTurns,
            int maxTurns,
            OffsetDateTime expiresAt) {
    }
}
