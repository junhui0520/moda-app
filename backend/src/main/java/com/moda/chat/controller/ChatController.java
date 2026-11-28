package com.moda.chat.controller;

import com.moda.chat.client.ChatClient.ChatOptions;
import com.moda.chat.dto.ChatDtos.ChatHistoryData;
import com.moda.chat.dto.ChatDtos.ChatSendData;
import com.moda.chat.dto.ChatDtos.ChatSendRequest;
import com.moda.chat.service.ChatService;
import com.moda.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 챗봇 API ⑨·⑩ (docs/api-spec.md v1.2). */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/chat/messages")
@Tag(name = "Chat", description = "결과 화면 이후 [챗봇 이용하기] — 대화 조회(⑨) · 메시지 전송(⑩)")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "⑨ 챗봇 대화 조회 (화면 진입)",
            description = "대화 내역 + 맞춤 첫 인사(welcomeMessage, 저장·턴 차감 없음) + 추천 질문 칩 3~4개 + 남은 턴. "
                    + "얼굴·체형 분석 완료 필요(409). 세션 TTL은 연장하지 않음")
    @GetMapping
    public ApiResponse<ChatHistoryData> getMessages(@PathVariable String sessionId) {
        return ApiResponse.success(chatService.getMessages(sessionId));
    }

    @Operation(summary = "⑩ 챗봇 메시지 전송",
            description = "message: trim 후 1~500자. 세션 분석 결과를 시스템 프롬프트로 자동 주입해 LLM 호출. "
                    + "성공 시에만 메시지 쌍 저장·턴 차감·TTL 30분 연장. 20턴 초과 429, LLM 장애 503, 타임아웃 504")
    @PostMapping
    public ApiResponse<ChatSendData> sendMessage(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatSendRequest request,
            @Parameter(description = "Mock(moda.chat.mode=mock): 답변 지연 0~10000ms (기본 800)")
            @RequestHeader(value = "X-Mock-Delay-Ms", required = false) String mockDelayMs) {
        return ApiResponse.success(chatService.send(sessionId, request.message(), new ChatOptions(mockDelayMs)));
    }
}
