package com.moda.session.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.ai.AiResponseParser;
import com.moda.ai.AnalysisOptions;
import com.moda.ai.ExternalAiAnalysisClient;
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
 * ③-L·④-L을 {@code moda.ai.mode=external}로 기동해 로컬 HTTP 서버(AI {@code /v1/live/*} 대역)와 연동 검증합니다.
 * 요청 JSON(rounds·quality·request_id만 전달, 체형 height_cm은 세션 값) · 응답 정규화 · 오류 변환(A.6, 422 → 502)을 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveAnalysisExternalTest extends LiveTestSupport {

    static final HttpServer SERVER;
    static final AtomicReference<Integer> STATUS = new AtomicReference<>(200);
    static final AtomicReference<String> BODY = new AtomicReference<>("{}");
    static final AtomicReference<Long> DELAY_MS = new AtomicReference<>(0L);
    static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
    static final AtomicReference<String> LAST_CONTENT_TYPE = new AtomicReference<>();
    static final AtomicReference<String> LAST_BODY = new AtomicReference<>();

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SERVER.setExecutor(Executors.newCachedThreadPool());
        SERVER.createContext("/", exchange -> {
            LAST_PATH.set(exchange.getRequestURI().getPath());
            LAST_CONTENT_TYPE.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            LAST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
    static void aiProperties(DynamicPropertyRegistry registry) {
        registry.add("moda.ai.mode", () -> "external");
        registry.add("moda.ai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("moda.ai.connect-timeout", () -> "1s");
        registry.add("moda.ai.face-read-timeout", () -> "700ms");
        registry.add("moda.ai.body-read-timeout", () -> "700ms");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void reset() {
        STATUS.set(200);
        DELAY_MS.set(0L);
        BODY.set(FACE_OK);
    }

    /** AI /v1/live/face 응답 (A.2 형식 + 실시간 추가 필드 n_rounds·n_frames·round_results — 파서가 무시). */
    static final String FACE_OK = """
            {"usable":true,"face_shape":"round","reason":"평균 대비 얼굴 길이 비율이 낮아 둥근형 특징이 보입니다.",
             "confidence":0.71,"borderline":false,
             "candidates":[{"key":"oval","prob":0.29},{"key":"round","prob":0.71}],
             "warnings":[],"features":{"length_ratio":1.08,"jaw_angle_deg":128.0},
             "model_version":"face-live-0.1","n_rounds":2,"n_frames":4,"round_results":["round","round"]}""";

    static final String BODY_OK = """
            {"usable":true,"body_type":"standard","confidence":0.66,"borderline":false,
             "candidates":[{"key":"standard","prob":0.66},{"key":"rectangle","prob":0.34}],
             "warnings":["BODY_LEGS_CLOSED"],"features":{"shoulder_dev":0.01,"trunk_dev":0.0},
             "model_version":"body-live-0.1","n_rounds":2,"n_frames":2,"round_results":["standard","standard"]}""";

    @Test
    @DisplayName("③-L: POST /v1/live/face (JSON) — rounds·quality·request_id만 전달, 앱이 보낸 다른 필드는 버림, 추가 응답 필드 무시")
    void faceLiveRequestAndResponse() throws Exception {
        String sid = profiledSession();
        String payload = FACE_PAYLOAD.replaceFirst("\\{", "{\"photo\":\"data:image/jpeg;base64,AAAA\",\"deviceId\":\"booth-01\",");

        JsonNode d = data(call(faceLive(sid, payload), 200));
        assertThat(d.get("faceShape").get("key").asText()).isEqualTo("round");
        assertThat(d.get("confidence").asDouble()).isEqualTo(0.71);
        assertProgress(d, true, true, false);

        assertThat(LAST_PATH.get()).isEqualTo("/v1/live/face");
        assertThat(LAST_CONTENT_TYPE.get()).startsWith("application/json");
        JsonNode sent = objectMapper.readTree(LAST_BODY.get());
        assertThat(sent.fieldNames()).toIterable().containsExactlyInAnyOrder("rounds", "quality", "request_id");
        assertThat(sent.get("rounds")).isEqualTo(objectMapper.readTree(FACE_PAYLOAD).get("rounds"));
        assertThat(sent.get("quality").get("yaw").asDouble()).isEqualTo(1.2);
        assertThat(sent.get("request_id").asText()).isNotBlank().isNotEqualTo(sid);           // 세션 ID를 AI로 보내지 않음

        JsonNode face = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200)).get("face");
        assertThat(face.get("aiReason").asText()).isEqualTo("평균 대비 얼굴 길이 비율이 낮아 둥근형 특징이 보입니다.");
        assertThat(face.get("candidates").get(0).get("key").asText()).isEqualTo("round");     // 확률 내림차순 정렬
    }

    @Test
    @DisplayName("④-L: POST /v1/live/body — height_cm은 세션 값(174), 앱이 보낸 height_cm(999)은 무시 / BODY_LEGS_CLOSED는 INFO 안내")
    void bodyLiveUsesSessionHeight() throws Exception {
        String sid = profiledSession();
        BODY.set(BODY_OK);
        String payload = BODY_PAYLOAD.replaceFirst("\\{", "{\"height_cm\":999,");

        JsonNode d = data(call(bodyLive(sid, payload), 200));
        assertThat(d.get("bodyType").get("key").asText()).isEqualTo("standard");
        assertThat(d.get("heightCmUsed").asInt()).isEqualTo(174);
        assertThat(d.get("warnings")).hasSize(1);
        assertThat(d.get("warnings").get(0).get("code").asText()).isEqualTo("BODY_LEGS_CLOSED");
        assertThat(d.get("warnings").get(0).get("severity").asText()).isEqualTo("INFO");
        assertThat(d.get("warnings").get(0).get("message").asText()).isEqualTo("다리를 살짝 벌리면 다리 비율까지 더 정확하게 볼 수 있어요.");

        assertThat(LAST_PATH.get()).isEqualTo("/v1/live/body");
        JsonNode sent = objectMapper.readTree(LAST_BODY.get());
        assertThat(sent.get("height_cm").asInt()).isEqualTo(174);
        assertThat(sent.fieldNames()).toIterable().containsExactlyInAnyOrder("rounds", "quality", "request_id", "height_cm");
    }

    @Test
    @DisplayName("측정 부족: AI 200 + usable=false + LIVE_NOT_ENOUGH_FRAMES → 재측정 안내(RETAKE, 시드 문구), progress 불변")
    void notEnoughFrames() throws Exception {
        String sid = profiledSession();
        BODY.set("{\"usable\":false,\"face_shape\":null,\"warnings\":[\"LIVE_NOT_ENOUGH_FRAMES\"],\"n_rounds\":0,\"n_frames\":0}");
        JsonNode d = data(call(faceLive(sid, FACE_PAYLOAD), 200));
        assertThat(d.get("usable").asBoolean()).isFalse();
        assertThat(d.get("retakeRequired").asBoolean()).isTrue();
        assertThat(d.get("warnings")).hasSize(1);                                              // RETAKE 코드가 있으므로 ANALYSIS_UNUSABLE 미추가
        assertThat(d.get("warnings").get(0).get("code").asText()).isEqualTo("LIVE_NOT_ENOUGH_FRAMES");
        assertThat(d.get("warnings").get(0).get("severity").asText()).isEqualTo("RETAKE");
        assertThat(d.get("warnings").get(0).get("message").asText()).isEqualTo("측정이 충분히 되지 않았어요. 안내에 맞춰 다시 측정해 주세요.");
        assertProgress(d, true, false, false);
    }

    @Test
    @DisplayName("오류 변환 (A.6): AI 422 → 502, 5xx → 503, 응답 지연 → 504 — 실패 후 결과 미반영·잠금 해제")
    void errorMapping() throws Exception {
        String sid = profiledSession();

        STATUS.set(422);
        BODY.set("{\"detail\":[{\"loc\":[\"body\",\"height_cm\"],\"msg\":\"Field required\"}]}");
        assertThat(assertError(call(bodyLive(sid, BODY_PAYLOAD), 502), "AI_BAD_RESPONSE").get("status").asInt()).isEqualTo(502);

        STATUS.set(500);
        BODY.set("{\"detail\":\"boom\"}");
        assertError(call(faceLive(sid, FACE_PAYLOAD), 503), "AI_SERVER_UNAVAILABLE");

        STATUS.set(200);
        BODY.set(FACE_OK);
        DELAY_MS.set(1500L);
        assertError(call(faceLive(sid, FACE_PAYLOAD), 504), "AI_TIMEOUT");

        STATUS.set(200);
        BODY.set("{\"usable\":true,\"face_shape\":\"triangle\",\"warnings\":[]}");            // 정의되지 않은 Key
        DELAY_MS.set(0L);
        assertError(call(faceLive(sid, FACE_PAYLOAD), 502), "AI_BAD_RESPONSE");

        JsonNode detail = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200));
        assertThat(detail.get("face").get("status").asText()).isEqualTo("NOT_ANALYZED");
        assertThat(detail.get("body").get("status").asText()).isEqualTo("NOT_ANALYZED");
        reset();
        data(call(faceLive(sid, FACE_PAYLOAD), 200));                                           // 잠금 해제 확인
    }

    @Test
    @DisplayName("회귀: 사진 분석 ③은 그대로 multipart로 /v1/analyze/face 호출")
    void photoAnalysisStillMultipart() throws Exception {
        String sid = profiledSession();
        org.springframework.mock.web.MockMultipartFile image = new org.springframework.mock.web.MockMultipartFile(
                "image", "face.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, 1});
        data(call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(BASE + "/" + sid + "/analyze/face").file(image), 200));
        assertThat(LAST_PATH.get()).isEqualTo("/v1/analyze/face");
        assertThat(LAST_CONTENT_TYPE.get()).startsWith("multipart/form-data");
    }

    @Test
    @DisplayName("연결 실패(포트 닫힘) → 503 AI_SERVER_UNAVAILABLE (클라이언트 단위)")
    void connectionRefused() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        ExternalAiAnalysisClient client = new ExternalAiAnalysisClient(new ModaProperties.Ai(ClientMode.EXTERNAL,
                "http://127.0.0.1:" + closedPort, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ZERO),
                new AiResponseParser(objectMapper));
        JsonNode measurement = objectMapper.readTree(BODY_PAYLOAD);
        assertThatThrownBy(() -> client.analyzeBodyLive(measurement, 174, "r1", AnalysisOptions.none()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_SERVER_UNAVAILABLE));
    }
}
