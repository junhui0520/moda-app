package com.moda.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.ai.model.BodyAnalysisResult;
import com.moda.ai.model.FaceAnalysisResult;
import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.global.config.ModaProperties;
import com.moda.global.config.ModaProperties.ClientMode;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ExternalAiAnalysisClient}를 로컬 HTTP 서버(JDK HttpServer)로 검증합니다 (api-spec.md 부록 A.1 ~ A.6).
 */
class ExternalAiAnalysisClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AnalysisImage IMAGE = new AnalysisImage(new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2}, "image/jpeg", "face.jpg", "req-1");

    private HttpServer server;
    private final AtomicReference<String> responseBody = new AtomicReference<>("{}");
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>(200);
    private final AtomicReference<Long> responseDelayMs = new AtomicReference<>(0L);
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> lastRequestPath = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastRequestPath.set(exchange.getRequestURI().getPath());
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            try {
                Thread.sleep(responseDelayMs.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ExternalAiAnalysisClient client(Duration readTimeout) {
        return client("http://127.0.0.1:" + server.getAddress().getPort(), readTimeout);
    }

    private static ExternalAiAnalysisClient client(String baseUrl, Duration readTimeout) {
        ModaProperties.Ai props = new ModaProperties.Ai(ClientMode.EXTERNAL, baseUrl, Duration.ofSeconds(1), readTimeout, readTimeout, Duration.ZERO);
        return new ExternalAiAnalysisClient(props, new AiResponseParser(MAPPER));
    }

    private void respond(int status, String body) {
        responseStatus.set(status);
        responseBody.set(body);
    }

    @Test
    @DisplayName("얼굴: A.2 전체 응답 + v8 reason 파싱, multipart(image, request_id) 전송")
    void faceFullResponse() {
        respond(200, """
                {"usable":true,"face_shape":"heart","reason":"평균 대비 이마 너비 비율이 높습니다.","confidence":0.6412,"borderline":false,
                 "candidates":[{"key":"diamond","prob":0.21},{"key":"heart","prob":0.64},{"key":"oval","prob":0.15}],
                 "warnings":[],"features":{"length_ratio":1.18,"jaw_angle_deg":141.5},"compare_to_size_korea":null,
                 "quality":{"yaw_deg":3.2},"model_version":"face-rule-0.1"}""");

        FaceAnalysisResult r = client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none());

        assertThat(r.usable()).isTrue();
        assertThat(r.faceShape()).isEqualTo(FaceShape.HEART);
        assertThat(r.reason()).isEqualTo("평균 대비 이마 너비 비율이 높습니다.");
        assertThat(r.confidence()).isEqualTo(0.641);
        assertThat(r.candidates()).extracting(c -> c.key()).containsExactly("heart", "diamond", "oval");  // 확률 내림차순 정렬
        assertThat(r.features()).extracting(f -> f.key()).containsExactly("length_ratio", "jaw_angle_deg");
        assertThat(r.modelVersion()).isEqualTo("face-rule-0.1");

        assertThat(lastRequestPath.get()).isEqualTo("/v1/analyze/face");
        assertThat(lastContentType.get()).startsWith("multipart/form-data");
        assertThat(lastRequestBody.get()).contains("name=\"image\"").contains("filename=\"face.jpg\"").contains("Content-Type: image/jpeg")
                .contains("name=\"request_id\"").doesNotContain("height_cm");
    }

    @Test
    @DisplayName("얼굴: reason 누락·null·숫자여도 502 없이 null (v8 이전 AI 서버 호환)")
    void faceReasonIsOptional() {
        String base = """
                {"usable":true,"face_shape":"oval","confidence":0.7,"borderline":false,
                 "candidates":[{"key":"oval","prob":0.7},{"key":"round","prob":0.3}],"warnings":[],"features":{"length_ratio":1.3}%s}""";
        for (String reason : new String[]{"", ",\"reason\":null", ",\"reason\":123", ",\"reason\":\"  \""}) {
            respond(200, base.formatted(reason));
            assertThat(client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()).reason()).as(reason).isNull();
        }
    }

    @Test
    @DisplayName("얼굴: 필수 필드 누락·정의되지 않은 Key → 502 AI_BAD_RESPONSE")
    void faceRequiredFields() {
        respond(200, "{\"usable\":true,\"face_shape\":\"heart\",\"borderline\":false,\"candidates\":[],\"warnings\":[],\"features\":{}}");
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()), ErrorCode.AI_BAD_RESPONSE);

        respond(200, "{\"usable\":true,\"face_shape\":\"triangle\",\"confidence\":0.6,\"borderline\":false,"
                + "\"candidates\":[{\"key\":\"oval\",\"prob\":1.0}],\"warnings\":[],\"features\":{}}");
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()), ErrorCode.AI_BAD_RESPONSE);

        respond(200, "not json");
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()), ErrorCode.AI_BAD_RESPONSE);
    }

    @Test
    @DisplayName("얼굴: usable=false는 오류가 아니다 (경고 코드만 전달)")
    void faceUnusable() {
        respond(200, "{\"usable\":false,\"face_shape\":null,\"warnings\":[\"FACE_YAW_EXCEEDED\",\"SMILING\"]}");
        FaceAnalysisResult r = client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none());
        assertThat(r.usable()).isFalse();
        assertThat(r.faceShape()).isNull();
        assertThat(r.warningCodes()).containsExactly("FACE_YAW_EXCEEDED", "SMILING");
    }

    @Test
    @DisplayName("체형: 최소 응답(usable·body_type·warnings)만으로 정상, height_cm 전송")
    void bodyMinimal() {
        respond(200, "{\"usable\":true,\"body_type\":\"small_inv_triangle\",\"warnings\":[]}");
        BodyAnalysisResult r = client(Duration.ofSeconds(5)).analyzeBody(IMAGE, 174, AnalysisOptions.none());
        assertThat(r.bodyType()).isEqualTo(BodyType.SMALL_INV_TRIANGLE);
        assertThat(r.confidence()).isNull();
        assertThat(r.borderline()).isNull();
        assertThat(r.candidates()).isEmpty();
        assertThat(r.features()).isEmpty();
        assertThat(r.compareToSizeKorea()).isNull();
        assertThat(lastRequestPath.get()).isEqualTo("/v1/analyze/body");
        assertThat(lastRequestBody.get()).contains("name=\"height_cm\"").contains("174");
    }

    @Test
    @DisplayName("체형: 선택 필드 형식 오류는 502 없이 해당 필드만 null/[]")
    void bodyOptionalFieldsTolerated() {
        respond(200, """
                {"usable":true,"body_type":"standard","warnings":[],"confidence":"high","borderline":"no",
                 "candidates":[{"key":"triangle","prob":0.9}],"features":{"shoulder_dev":"x"},
                 "compare_to_size_korea":{"reference":"SIZE_KOREA_20S_MALE"}}""");
        BodyAnalysisResult r = client(Duration.ofSeconds(5)).analyzeBody(IMAGE, 174, AnalysisOptions.none());
        assertThat(r.bodyType()).isEqualTo(BodyType.STANDARD);
        assertThat(r.confidence()).isNull();
        assertThat(r.borderline()).isNull();
        assertThat(r.candidates()).isEmpty();
        assertThat(r.features()).isEmpty();
        assertThat(r.compareToSizeKorea()).isNull();
    }

    @Test
    @DisplayName("체형: 전체 응답 — Size Korea 비교 + summary는 features(shoulder_dev, trunk_dev)로 보완")
    void bodyFullResponse() {
        respond(200, """
                {"usable":true,"body_type":"small_inv_triangle","confidence":0.68,"borderline":false,
                 "candidates":[{"key":"small_inv_triangle","prob":0.68},{"key":"standard","prob":0.32}],"warnings":[],
                 "features":{"shoulder_dev":0.040,"trunk_dev":-0.031,"shoulder_minus_trunk":0.071},
                 "compare_to_size_korea":{"reference":"SIZE_KOREA_20S_MALE","height_cm":174,
                   "items":[{"key":"bideltoid_width","user_mm":491,"ref_mm":470,"deviation":0.040}]}}""");
        BodyAnalysisResult r = client(Duration.ofSeconds(5)).analyzeBody(IMAGE, 174, AnalysisOptions.none());
        assertThat(r.compareToSizeKorea().items()).hasSize(1);
        assertThat(r.compareToSizeKorea().summary().shoulderDeviation()).isEqualTo(0.04);
        assertThat(r.compareToSizeKorea().summary().trunkDeviation()).isEqualTo(-0.031);
    }

    @Test
    @DisplayName("체형: 필수 body_type이 정의되지 않은 Key → 502")
    void bodyRequiredKey() {
        respond(200, "{\"usable\":true,\"body_type\":\"triangle\",\"warnings\":[]}");
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeBody(IMAGE, 174, AnalysisOptions.none()), ErrorCode.AI_BAD_RESPONSE);
        respond(200, "{\"usable\":true,\"body_type\":\"standard\"}");                 // warnings 누락
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeBody(IMAGE, 174, AnalysisOptions.none()), ErrorCode.AI_BAD_RESPONSE);
    }

    @Test
    @DisplayName("A.6 오류 변환: 5xx → 503, 4xx → 502, 연결 실패 → 503, 읽기 타임아웃 → 504")
    void errorMapping() throws IOException {
        respond(500, "{\"detail\":\"boom\"}");
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()), ErrorCode.AI_SERVER_UNAVAILABLE);

        respond(422, "{\"detail\":\"bad\"}");
        assertCode(() -> client(Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()), ErrorCode.AI_BAD_RESPONSE);

        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        assertCode(() -> client("http://127.0.0.1:" + closedPort, Duration.ofSeconds(5)).analyzeFace(IMAGE, AnalysisOptions.none()),
                ErrorCode.AI_SERVER_UNAVAILABLE);

        respond(200, "{\"usable\":false,\"face_shape\":null,\"warnings\":[]}");
        responseDelayMs.set(1500L);
        assertCode(() -> client(Duration.ofMillis(300)).analyzeFace(IMAGE, AnalysisOptions.none()), ErrorCode.AI_TIMEOUT);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(code));
    }
}
