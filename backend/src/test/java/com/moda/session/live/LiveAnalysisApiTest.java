package com.moda.session.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.session.service.AnalysisInProgressRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ③-L·④-L 실시간 분석 API (moda.ai.mode=mock). 응답 형식·흐름이 사진 분석 ③·④와 같은지, rounds 검증·선행 조건·Mock 헤더를 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveAnalysisApiTest extends LiveTestSupport {

    @Autowired AnalysisInProgressRegistry inProgressRegistry;

    @Test
    @DisplayName("③-L → ④-L → ⑤ → ⑥: 사진 없이 실시간 측정만으로 전체 흐름 (TYPE-08), TTL 연장")
    void fullFlowWithLiveAnalysis() throws Exception {
        String sid = profiledSession();
        setExpiresAt(sid, Instant.now().plus(Duration.ofMinutes(5)));

        JsonNode face = data(call(faceLive(sid, FACE_PAYLOAD).header("X-Mock-Face-Shape", "heart"), 200));
        assertThat(face.get("analysisType").asText()).isEqualTo("FACE");
        assertThat(face.get("usable").asBoolean()).isTrue();
        assertThat(face.get("retakeRequired").asBoolean()).isFalse();
        assertThat(face.get("faceShape").get("label").asText()).isEqualTo("하트형");
        assertThat(face.get("confidence").asDouble()).isEqualTo(0.64);
        assertThat(face.get("nextStep").asText()).isEqualTo("BODY_ANALYSIS");
        assertProgress(face, true, true, false);
        assertThat(OffsetDateTime.parse(face.get("expiresAt").asText()).toInstant())
                .as("쓰기 요청 성공 → TTL 30분 연장").isAfter(Instant.now().plus(Duration.ofMinutes(29)));

        JsonNode body = data(call(bodyLive(sid, BODY_PAYLOAD).header("X-Mock-Body-Type", "small_inv_triangle"), 200));
        assertThat(body.get("analysisType").asText()).isEqualTo("BODY");
        assertThat(body.get("bodyType").get("key").asText()).isEqualTo("small_inv_triangle");
        assertThat(body.get("heightCmUsed").asInt()).isEqualTo(174);
        assertThat(body.get("nextStep").asText()).isEqualTo("RESULT_READY");

        JsonNode result = data(call(get(BASE + "/" + sid + "/result"), 200));
        assertThat(result.get("type").get("typeCode").asText()).isEqualTo("TYPE-08");

        JsonNode detail = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200));
        assertThat(detail.get("face").get("status").asText()).isEqualTo("ANALYZED");
        assertThat(detail.get("face").get("aiReason").asText()).contains("하트형");
        assertThat(detail.get("body").get("compareToSizeKorea").get("items")).hasSize(6);
    }

    @Test
    @DisplayName("③-L은 기본 정보 전에도 가능, ④-L은 기본 정보 전 409 PROFILE_INPUT_REQUIRED (rounds 검사보다 먼저)")
    void profileInputRequired() throws Exception {
        String sid = newSession();
        data(call(faceLive(sid, FACE_PAYLOAD), 200));
        assertError(call(bodyLive(sid, BODY_PAYLOAD), 409), "PROFILE_INPUT_REQUIRED");
        assertError(call(bodyLive(sid, "{}"), 409), "PROFILE_INPUT_REQUIRED");
    }

    @Nested
    @DisplayName("Mock 헤더 (③·④와 동일)")
    class MockHeaders {

        @Test
        @DisplayName("X-Mock-Unusable: 200 + usable=false, 재측정 경고, progress 불변")
        void unusable() throws Exception {
            String sid = profiledSession();
            JsonNode d = data(call(faceLive(sid, FACE_PAYLOAD).header("X-Mock-Unusable", "true"), 200));
            assertThat(d.get("usable").asBoolean()).isFalse();
            assertThat(d.get("retakeRequired").asBoolean()).isTrue();
            assertThat(d.get("faceShape").isNull()).isTrue();
            assertThat(d.get("warnings").get(0).get("severity").asText()).isEqualTo("RETAKE");
            assertProgress(d, true, false, false);
        }

        @Test
        @DisplayName("X-Mock-Borderline: confidence 0.52 + FACE_BORDERLINE(INFO)")
        void borderline() throws Exception {
            String sid = profiledSession();
            JsonNode d = data(call(faceLive(sid, FACE_PAYLOAD).header("X-Mock-Borderline", "true"), 200));
            assertThat(d.get("confidence").asDouble()).isEqualTo(0.52);
            assertThat(d.get("warnings").get(0).get("code").asText()).isEqualTo("FACE_BORDERLINE");
        }

        @Test
        @DisplayName("X-Mock-Body-Minimal (④-L): 체형만 확정, confidence·borderline null")
        void bodyMinimal() throws Exception {
            String sid = profiledSession();
            JsonNode d = data(call(bodyLive(sid, BODY_PAYLOAD).header("X-Mock-Body-Minimal", "true")
                    .header("X-Mock-Body-Type", "rectangle"), 200));
            assertThat(d.get("bodyType").get("key").asText()).isEqualTo("rectangle");
            assertThat(d.has("confidence") && d.get("confidence").isNull()).isTrue();
            assertThat(d.has("borderline") && d.get("borderline").isNull()).isTrue();
        }

        @Test
        @DisplayName("잘못된 Mock 헤더 → 400 INVALID_REQUEST")
        void invalidHeader() throws Exception {
            String sid = profiledSession();
            JsonNode err = assertError(call(faceLive(sid, FACE_PAYLOAD).header("X-Mock-Face-Shape", "triangle"), 400), "INVALID_REQUEST");
            assertThat(err.get("details").get(0).get("field").asText()).isEqualTo("X-Mock-Face-Shape");
        }
    }

    @Nested
    @DisplayName("rounds 검증 → 400 INVALID_REQUEST (AI 호출 전)")
    class MeasurementValidation {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                rounds 누락          | {"quality":{"yaw":1}}                      | rounds    | 측정값(rounds)이 필요해요.
                rounds 빈 배열        | {"rounds":[]}                              | rounds    | 측정값(rounds)이 필요해요.
                rounds 문자열         | {"rounds":"x"}                             | rounds    | 측정값(rounds)이 필요해요.
                최상위가 배열          | [[{"a":1}]]                                | rounds    | 측정값(rounds)이 필요해요.
                회차가 배열 아님       | {"rounds":[1]}                             | rounds[0] | 회차는 프레임 측정값 배열이어야 해요.
                프레임이 객체 아님     | {"rounds":[[{"a":1}],[1]]}                 | rounds[1] | 프레임 측정값은 객체여야 해요.
                회차 6개 초과          | {"rounds":[[],[],[],[],[],[]]}             | rounds    | 회차는 최대 5개까지 보낼 수 있어요.
                """)
        void invalidRounds(String caseName, String body, String field, String reason) throws Exception {
            String sid = profiledSession();
            JsonNode err = assertError(call(faceLive(sid, body), 400), "INVALID_REQUEST");
            assertThat(err.get("details").get(0).get("field").asText()).isEqualTo(field);
            assertThat(err.get("details").get(0).get("reason").asText()).isEqualTo(reason);
            assertError(call(bodyLive(sid, body), 400), "INVALID_REQUEST");                // ④-L도 같은 규칙
            JsonNode detail = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200));
            assertThat(detail.get("face").get("status").asText()).as("검증 실패 시 분석 결과 없음").isEqualTo("NOT_ANALYZED");
            assertThat(detail.get("body").get("status").asText()).isEqualTo("NOT_ANALYZED");
            assertThat(inProgressRegistry.isInProgress(sid)).as("잠금을 잡기 전에 거절").isFalse();
        }

        @Test
        @DisplayName("회차당 프레임 60개 초과 → 400")
        void tooManyFrames() throws Exception {
            String sid = profiledSession();
            String frames = String.join(",", java.util.Collections.nCopies(61, "{\"a\":1}"));
            JsonNode err = assertError(call(faceLive(sid, "{\"rounds\":[[" + frames + "]]}"), 400), "INVALID_REQUEST");
            assertThat(err.get("details").get(0).get("field").asText()).isEqualTo("rounds[0]");
            assertThat(err.get("details").get(0).get("rejectedValue").asInt()).isEqualTo(61);
        }

        @Test
        @DisplayName("본문 없음 · JSON 문법 오류 → 400, JSON이 아닌 Content-Type → 415 (공통 에러 포맷)")
        void malformedRequests() throws Exception {
            String sid = profiledSession();
            assertError(call(post(BASE + "/" + sid + "/analyze/face/live").contentType(MediaType.APPLICATION_JSON), 400), "INVALID_REQUEST");
            assertError(call(faceLive(sid, "{\"rounds\":["), 400), "INVALID_REQUEST");
            JsonNode err = assertError(call(post(BASE + "/" + sid + "/analyze/face/live").contentType(MediaType.TEXT_PLAIN).content("x"), 415),
                    "INVALID_REQUEST");
            assertThat(err.get("status").asInt()).isEqualTo(415);
        }
    }

    @Nested
    @DisplayName("세션 · 잠금")
    class SessionRules {

        @Test
        @DisplayName("404 SESSION_NOT_FOUND / 410 SESSION_EXPIRED")
        void notFoundAndExpired() throws Exception {
            assertError(call(faceLive(UUID.randomUUID().toString(), FACE_PAYLOAD), 404), "SESSION_NOT_FOUND");
            String sid = profiledSession();
            setExpiresAt(sid, Instant.now().minusSeconds(5));
            assertError(call(faceLive(sid, FACE_PAYLOAD), 410), "SESSION_EXPIRED");
            assertError(call(bodyLive(sid, BODY_PAYLOAD), 410), "SESSION_EXPIRED");
        }

        @Test
        @DisplayName("분석 진행 중 재요청 → 409 ANALYSIS_IN_PROGRESS (사진 ③과 같은 세션 잠금), 완료 후 해제")
        void analysisInProgress() throws Exception {
            String sid = profiledSession();
            CompletableFuture<MvcResult> slow = CompletableFuture.supplyAsync(() -> {
                try {
                    return mvc.perform(faceLive(sid, FACE_PAYLOAD).header("X-Mock-Delay-Ms", "1500")).andReturn();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            long deadline = System.currentTimeMillis() + 3000;
            while (!inProgressRegistry.isInProgress(sid) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertError(call(bodyLive(sid, BODY_PAYLOAD), 409), "ANALYSIS_IN_PROGRESS");
            assertThat(slow.get().getResponse().getStatus()).isEqualTo(200);
            assertThat(inProgressRegistry.isInProgress(sid)).isFalse();
            data(call(bodyLive(sid, BODY_PAYLOAD), 200));
        }
    }
}
