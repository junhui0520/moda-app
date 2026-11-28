package com.moda.chat.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moda.common.enums.ChatRole;
import com.moda.global.config.ModaProperties;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code moda.chat.mode=external} — OpenAI 호환 Chat Completions API 클라이언트.
 * <pre>
 * POST {moda.chat.base-url}/chat/completions
 * Authorization: Bearer {moda.chat.api-key}
 * {"model": ..., "messages": [{"role":"system",...}, ...이전 대화, {"role":"user",...}], "temperature": ..., "max_tokens": ...}
 * → choices[0].message.content
 * </pre>
 * {@code base-url}·{@code model}·{@code api-key}만 바꾸면 OpenAI, Gemini(OpenAI 호환 엔드포인트) 등에 연결됩니다.
 * <p>오류 변환 (api-spec.md ⑩): 연결 실패·LLM {@code 429}·{@code 5xx}·인증 오류 등 모든 비정상 응답·빈 답변 → {@code 503 CHAT_SERVER_UNAVAILABLE},
 * 응답 대기 타임아웃 → {@code 504 CHAT_TIMEOUT}. LLM 제공사의 {@code 429}는 앱에 {@code 429}로 전달하지 않습니다(앱의 429는 턴 한도 전용).
 */
public class ExternalLlmChatClient implements ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalLlmChatClient.class);

    static final String COMPLETIONS_PATH = "/chat/completions";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModaProperties.Chat props;

    public ExternalLlmChatClient(ModaProperties.Chat props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(props.readTimeout());
        String baseUrl = props.baseUrl().endsWith("/") ? props.baseUrl().substring(0, props.baseUrl().length() - 1) : props.baseUrl();
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
        } else {
            log.warn("[Chat] moda.chat.mode=external 이지만 moda.chat.api-key(MODA_CHAT_API_KEY)가 비어 있습니다.");
        }
        this.restClient = builder.build();
    }

    @Override
    public ChatReply complete(ChatCompletionCommand command) {
        String responseBody = post(requestBody(command));
        return new ChatReply(extractContent(responseBody), props.model());
    }

    String requestBody(ChatCompletionCommand command) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", props.model());
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", command.systemPrompt());
        for (ChatTurn turn : command.history()) {
            messages.addObject().put("role", turn.role() == ChatRole.USER ? "user" : "assistant").put("content", turn.content());
        }
        messages.addObject().put("role", "user").put("content", command.userMessage());
        root.put("temperature", props.temperature());
        root.put("max_tokens", props.maxTokens());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Chat Completions 요청 직렬화 실패", e);
        }
    }

    private String post(String body) {
        try {
            return restClient.post()
                    .uri(COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new BusinessException(ErrorCode.CHAT_SERVER_UNAVAILABLE, null,
                                new IllegalStateException("LLM " + COMPLETIONS_PATH + " → HTTP " + res.getStatusCode().value()));
                    })
                    .body(String.class);
        } catch (BusinessException e) {
            log.warn("[Chat] LLM 오류 응답: {}", e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            throw e;
        } catch (ResourceAccessException e) {
            if (isReadTimeout(e)) {
                throw new BusinessException(ErrorCode.CHAT_TIMEOUT, null, e);
            }
            throw new BusinessException(ErrorCode.CHAT_SERVER_UNAVAILABLE, null, e);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.CHAT_SERVER_UNAVAILABLE, null, e);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode content = objectMapper.readTree(responseBody == null ? "" : responseBody)
                    .path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw unavailable("빈 답변 (choices[0].message.content 없음)");
            }
            return content.asText();
        } catch (JsonProcessingException e) {
            throw unavailable("응답 JSON 파싱 실패: " + e.getOriginalMessage());
        }
    }

    private static BusinessException unavailable(String detail) {
        log.warn("[Chat] {}", detail);
        return new BusinessException(ErrorCode.CHAT_SERVER_UNAVAILABLE, null, new IllegalStateException(detail));
    }

    /** 연결 타임아웃·연결 거부는 503, 연결 후 응답 대기 타임아웃만 504. */
    static boolean isReadTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains("connect timed out")) {
                return false;
            }
        }
        return false;
    }
}
