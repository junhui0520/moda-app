package com.moda.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.chat.client.ChatClient.ChatCompletionCommand;
import com.moda.chat.client.ChatClient.ChatOptions;
import com.moda.chat.client.ExternalLlmChatClient;
import com.moda.global.config.ModaProperties;
import com.moda.global.config.ModaProperties.ClientMode;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link ExternalLlmChatClient} 검증 — 로컬 HTTP 서버를 OpenAI 호환 {@code /chat/completions}로 띄우고
 * {@code moda.chat.mode=external}로 애플리케이션을 기동해 ⑩ 전체 경로(요청 JSON · 오류 변환 · 실패 시 미저장)를 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExternalLlmChatClientTest extends ChatTestSupport {

    static final HttpServer SERVER;
    static final AtomicReference<Integer> STATUS = new AtomicReference<>(200);
    static final AtomicReference<String> BODY = new AtomicReference<>("{}");
    static final AtomicReference<Long> DELAY_MS = new AtomicReference<>(0L);
    static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>();
    static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
    static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SERVER.setExecutor(Executors.newCachedThreadPool());
        SERVER.createContext("/", exchange -> {
            LAST_PATH.set(exchange.getRequestURI().getPath());
            LAST_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
            LAST_REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(DELAY_MS.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = BODY.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(STATUS.get(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (IOException ignored) {
                // 클라이언트 타임아웃으로 연결이 먼저 끊긴 경우
            }
        });
        SERVER.start();
    }

    @DynamicPropertySource
    static void chatProperties(DynamicPropertyRegistry registry) {
        registry.add("moda.chat.mode", () -> "external");
        registry.add("moda.chat.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1/");
        registry.add("moda.chat.api-key", () -> "test-key");
        registry.add("moda.chat.model", () -> "test-model");
        registry.add("moda.chat.read-timeout", () -> "700ms");
        registry.add("moda.chat.connect-timeout", () -> "1s");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void resetServer() {
        STATUS.set(200);
        DELAY_MS.set(0L);
        BODY.set(completion("반가워요. 가르마 스타일부터 시작해 보세요."));
    }

    static String completion(String content) {
        return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion\",\"model\":\"test-model\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":" + com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode(content)
                + "},\"finish_reason\":\"stop\"}]}";
    }

    // ==================================================================================== 정상

    @Test
    @DisplayName("요청 JSON: /v1/chat/completions · Bearer 키 · model · system 프롬프트(몸무게/BMI 미포함) · 이전 대화 · max_tokens")
    void requestPayload() throws Exception {
        String sid = readySession();
        BODY.set(completion("**가르마 스타일**이 잘 어울려요.\n# 팁\n뿌리 볼륨을 살려 보세요. 가벼운 제품을 쓰면 좋아요."));

        JsonNode d = data(call(chatPost(sid, "가르마 스타일은 어떻게 연출하면 좋을까요?"), 200));
        assertThat(d.get("assistantMessage").get("content").asText())
                .as("마크다운 기호 제거").isEqualTo("가르마 스타일이 잘 어울려요.\n팁\n뿌리 볼륨을 살려 보세요. 가벼운 제품을 쓰면 좋아요.");
        assertThat(d.get("remainingTurns").asInt()).isEqualTo(19);

        assertThat(LAST_PATH.get()).isEqualTo("/v1/chat/completions");
        assertThat(LAST_AUTH.get()).isEqualTo("Bearer test-key");
        JsonNode req = objectMapper.readTree(LAST_REQUEST.get());
        assertThat(req.get("model").asText()).isEqualTo("test-model");
        assertThat(req.get("max_tokens").asInt()).isEqualTo(400);
        assertThat(req.get("temperature").asDouble()).isEqualTo(0.7);
        JsonNode messages = req.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("system");
        String system = messages.get(0).get("content").asText();
        assertThat(system).contains("V라인 프레시 (TYPE-08)").contains("3~5문장 이내").contains("체중 관련 수치는 절대 언급");
        ChatApiIntegrationTest.assertNoBodyMetricValues(system);
        assertThat(LAST_REQUEST.get()).doesNotContain("weightKg").doesNotContain("\"63\"").doesNotContain("20.8");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).get("content").asText()).isEqualTo("가르마 스타일은 어떻게 연출하면 좋을까요?");

        // 두 번째 턴: system + (user, assistant) + user
        call(chatPost(sid, "왁스 대신 쓸 제품은?"), 200);
        JsonNode second = objectMapper.readTree(LAST_REQUEST.get()).get("messages");
        assertThat(second.findValuesAsText("role")).containsExactly("system", "user", "assistant", "user");
        assertThat(second.get(2).get("content").asText()).startsWith("가르마 스타일이 잘 어울려요.");
        assertThat(chatCount(sid)).isEqualTo(4);
    }

    @Test
    @DisplayName("안전망: LLM 답변에 체중 수치가 있으면 핏 중심 안내 문구로 대체해 저장")
    void sanitizesBodyMetricNumbers() throws Exception {
        String sid = readySession();
        BODY.set(completion("체중 63kg 기준으로 보면 댄디 룩이 좋아요."));
        String reply = data(call(chatPost(sid, "어떤 옷이 좋을까요?"), 200)).get("assistantMessage").get("content").asText();
        assertThat(reply).doesNotContain("63").doesNotContain("kg").contains("수치는 따로 안내하지 않고");
        assertThat(chatRepository.findHistory(sid).get(1).getContent()).isEqualTo(reply);
    }

    // ==================================================================================== 실패 → 미저장 · 턴 미차감 · TTL 미연장

    @Test
    @DisplayName("LLM 429 → 503 CHAT_SERVER_UNAVAILABLE (앱 429와 구분), DB 미저장 · 턴 미차감 · TTL 미연장")
    void llmRateLimitMapsTo503() throws Exception {
        assertFailureNotPersisted(429, "{\"error\":{\"message\":\"Rate limit\"}}", 0, "CHAT_SERVER_UNAVAILABLE", 503);
    }

    @Test
    @DisplayName("LLM 5xx → 503, 인증 오류 401 → 503")
    void llmServerErrorsMapTo503() throws Exception {
        assertFailureNotPersisted(500, "{\"error\":\"boom\"}", 0, "CHAT_SERVER_UNAVAILABLE", 503);
        assertFailureNotPersisted(503, "{}", 0, "CHAT_SERVER_UNAVAILABLE", 503);
        assertFailureNotPersisted(401, "{\"error\":{\"message\":\"Invalid API key\"}}", 0, "CHAT_SERVER_UNAVAILABLE", 503);
    }

    @Test
    @DisplayName("빈 답변(choices 없음)·JSON 오류 → 503")
    void emptyReplyMapsTo503() throws Exception {
        assertFailureNotPersisted(200, "{\"choices\":[]}", 0, "CHAT_SERVER_UNAVAILABLE", 503);
        assertFailureNotPersisted(200, "not-json", 0, "CHAT_SERVER_UNAVAILABLE", 503);
    }

    @Test
    @DisplayName("응답 지연(읽기 타임아웃 700ms 초과) → 504 CHAT_TIMEOUT, 미저장")
    void timeoutMapsTo504() throws Exception {
        assertFailureNotPersisted(200, completion("늦은 답변"), 1500, "CHAT_TIMEOUT", 504);
    }

    @Test
    @DisplayName("연결 실패(포트 닫힘) → 503 CHAT_SERVER_UNAVAILABLE")
    void connectionRefusedMapsTo503() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        ModaProperties.Chat props = new ModaProperties.Chat(ClientMode.EXTERNAL, 20, Duration.ofSeconds(1), Duration.ofSeconds(2),
                "k", "http://127.0.0.1:" + closedPort + "/v1", "m", 0.7, 400, Duration.ZERO);
        ExternalLlmChatClient client = new ExternalLlmChatClient(props, objectMapper);
        assertThatThrownBy(() -> client.complete(new ChatCompletionCommand("sys", List.of(), "hi", null, ChatOptions.none())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CHAT_SERVER_UNAVAILABLE));
    }

    private void assertFailureNotPersisted(int status, String body, long delayMs, String code, int expectedHttp) throws Exception {
        String sid = readySession();
        call(chatPost(sid, "첫 질문"), 200);                                         // 정상 1턴 (실패가 기존 데이터에 영향 없는지 확인)
        Instant fixedExpiry = Instant.now().plus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        setExpiresAt(sid, fixedExpiry);

        STATUS.set(status);
        BODY.set(body);
        DELAY_MS.set(delayMs);
        JsonNode err = assertError(call(chatPost(sid, "실패할 질문"), expectedHttp), code);
        assertThat(err.get("status").asInt()).isEqualTo(expectedHttp);

        assertThat(chatCount(sid)).as("실패 시 메시지 미저장").isEqualTo(2);
        assertThat(expiresAt(sid)).as("실패 시 TTL 미연장").isEqualTo(fixedExpiry);
        resetServer();
        JsonNode h = data(call(chatGet(sid), 200));
        assertThat(h.get("remainingTurns").asInt()).as("실패 시 턴 미차감").isEqualTo(19);
        assertThat(h.get("messages").findValuesAsText("content")).doesNotContain("실패할 질문");
    }
}
