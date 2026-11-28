package com.moda.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.session.domain.GuestSession;
import com.moda.session.repository.GuestSessionRepository;
import com.moda.session.service.AnalysisInProgressRegistry;
import com.moda.session.service.SessionCleanupScheduler;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * API ①~⑧ 통합 테스트 (Mock AI 모드, H2). 응답은 JSON 트리로 검사해 키 존재·null 직렬화까지 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionApiIntegrationTest {

    static final String BASE = "/api/v1/sessions";
    static final String PROFILE_S006 = """
            {"age":21,"heightCm":174,"weightKg":63,"hairThickness":"THIN","hairCurl":"SEMI_CURLY","hairDensity":"MEDIUM"}""";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired GuestSessionRepository repository;
    @Autowired AnalysisInProgressRegistry inProgressRegistry;
    @Autowired SessionCleanupScheduler cleanupScheduler;

    // ==================================================================================== 정상 흐름

    @Test
    @DisplayName("① → ② → ③ → ④ → ⑤ → ⑥ → ⑦ → ⑧ 전체 정상 흐름 (api-spec.md 예시 체험자: TYPE-08)")
    void fullHappyPath() throws Exception {
        // ① 게스트 세션 생성
        JsonNode created = call(post(BASE + "/guest").contentType(MediaType.APPLICATION_JSON)
                .content("{\"photoConsent\":true,\"deviceLabel\":\"booth-01\"}"), 201);
        assertThat(created.get("success").asBoolean()).isTrue();
        assertThat(created.has("error")).as("성공 응답에는 error 키가 없다").isFalse();
        JsonNode d1 = created.get("data");
        String sid = d1.get("sessionId").asText();
        assertThat(UUID.fromString(sid)).isNotNull();
        assertThat(d1.get("ttlMinutes").asInt()).isEqualTo(30);
        assertThat(d1.get("nextStep").asText()).isEqualTo("PROFILE_INPUT");
        assertThat(d1.get("createdAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00");
        assertProgress(d1, false, false, false);

        // ② 기본 정보
        JsonNode d2 = data(call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006), 200));
        assertThat(d2.get("profileInput").get("hairCurl").asText()).isEqualTo("SEMI_CURLY");
        assertThat(d2.get("bodyAnalysisReset").asBoolean()).isFalse();
        assertThat(d2.get("nextStep").asText()).isEqualTo("FACE_ANALYSIS");
        assertProgress(d2, true, false, false);

        // ③ 얼굴 분석 (Mock: 하트형)
        JsonNode d3 = data(call(face(sid).header("X-Mock-Face-Shape", "heart"), 200));
        assertThat(d3.get("analysisType").asText()).isEqualTo("FACE");
        assertThat(d3.get("usable").asBoolean()).isTrue();
        assertThat(d3.get("retakeRequired").asBoolean()).isFalse();
        assertThat(d3.get("faceShape").get("key").asText()).isEqualTo("heart");
        assertThat(d3.get("faceShape").get("label").asText()).isEqualTo("하트형");
        assertThat(d3.get("confidence").asDouble()).isEqualTo(0.64);
        assertThat(d3.get("borderline").asBoolean()).isFalse();
        assertThat(d3.get("warnings")).isEmpty();
        assertThat(d3.get("nextStep").asText()).isEqualTo("BODY_ANALYSIS");
        assertProgress(d3, true, true, false);

        // ④ 전신 분석 (Mock: 작은 역삼각형, 키는 세션 값 자동 사용)
        JsonNode d4 = data(call(body(sid).header("X-Mock-Body-Type", "small_inv_triangle"), 200));
        assertThat(d4.get("analysisType").asText()).isEqualTo("BODY");
        assertThat(d4.get("bodyType").get("label").asText()).isEqualTo("작은 역삼각형");
        assertThat(d4.get("heightCmUsed").asInt()).isEqualTo(174);
        assertThat(d4.get("confidence").asDouble()).isEqualTo(0.68);
        assertThat(d4.get("nextStep").asText()).isEqualTo("RESULT_READY");
        assertProgress(d4, true, true, true);

        // ⑤ 결과
        JsonNode d5 = data(call(get(BASE + "/" + sid + "/result"), 200));
        assertThat(d5.get("sessionId").asText()).isEqualTo(sid);
        assertThat(d5.get("ruleVersion").asText()).isEqualTo("v1.1-draft");
        JsonNode type = d5.get("type");
        assertThat(type.get("typeCode").asText()).isEqualTo("TYPE-08");
        assertThat(type.get("typeName").asText()).isEqualTo("V라인 프레시");
        assertThat(type.get("nameConfirmed").asBoolean()).isFalse();
        assertThat(type.get("headline").asText()).isEqualTo("당신은 V라인 프레시 유형입니다");
        assertThat(type.get("stylePoints")).hasSize(2);
        assertThat(d5.get("basis").get("faceSource").asText()).isEqualTo("AI");
        assertThat(d5.get("basis").get("bodyType").get("key").asText()).isEqualTo("small_inv_triangle");
        assertThat(codes(d5.get("hairStyles"))).containsExactly("PARTED_STYLE", "SHADOW_STYLE");
        assertThat(codes(d5.get("fashionStyles"))).containsExactly("DANDY", "SPORTY", "CASUAL");
        JsonNode parted = d5.get("hairStyles").get(0);
        assertThat(parted.get("rank").asInt()).isEqualTo(1);
        assertThat(texts(parted.get("reasonCodes"))).containsExactly("FACE_HEART_PARTED_STYLE", "ADJ_CURL_SEMI_CURLY_PARTED_STYLE");
        assertThat(parted.get("scoreBreakdown").get("adjustments").get(0).get("factor").asText()).isEqualTo("hairCurl");
        assertThat(texts(d5.get("fashionStyles").get(0).get("itemKeywords"))).containsExactly("블레이저", "셔츠", "니트 폴로", "슬랙스", "로퍼");
        assertThat(d5.get("disclaimer").asText()).isEqualTo("분석 결과는 스타일 참고용이며, 언제든 [분석 상세보기]에서 직접 수정할 수 있어요.");

        // ⑥ 분석 상세보기
        JsonNode d6 = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200));
        JsonNode face = d6.get("face");
        assertThat(face.get("status").asText()).isEqualTo("ANALYZED");
        assertThat(face.get("source").asText()).isEqualTo("AI");
        assertThat(face.get("description").asText()).isEqualTo("이마 라인이 시원하고 턱선이 갸름하게 모여 산뜻한 인상을 주는 얼굴형입니다.");
        assertThat(face.get("aiReason").asText()).contains("하트형");
        assertThat(face.get("candidates")).hasSize(6);
        assertThat(face.get("candidates").get(0).get("key").asText()).isEqualTo("heart");
        assertThat(face.get("candidates").get(0).get("label").asText()).isEqualTo("하트형");
        assertThat(face.get("features")).hasSize(5);
        assertThat(face.get("features").get(0).get("label").asText()).isEqualTo("얼굴 길이 비율");
        assertThat(face.get("features").get(0).get("description").asText()).isEqualTo("얼굴 길이 ÷ 광대 폭");
        assertThat(face.has("compareToSizeKorea")).as("얼굴에는 Size Korea 비교 없음").isFalse();

        JsonNode bodyDetail = d6.get("body");
        assertThat(bodyDetail.has("aiReason")).as("BodyDetail에는 aiReason이 없다").isFalse();
        assertThat(bodyDetail.get("status").asText()).isEqualTo("ANALYZED");
        assertThat(bodyDetail.get("heightCmUsed").asInt()).isEqualTo(174);
        assertThat(bodyDetail.get("features").findValuesAsText("unit")).containsOnly("percent");
        assertThat(bodyDetail.get("features").get(0).get("value").asDouble()).isEqualTo(4.0);   // 0.040 × 100
        assertThat(bodyDetail.get("features").get(1).get("value").asDouble()).isEqualTo(-3.1);
        assertThat(bodyDetail.get("features").get(2).get("value").asDouble()).isEqualTo(7.1);
        JsonNode sk = bodyDetail.get("compareToSizeKorea");
        assertThat(sk.get("referenceLabel").asText()).isEqualTo("Size Korea 20대 남성 평균");
        assertThat(sk.get("heightCm").asInt()).isEqualTo(174);
        assertThat(sk.get("items")).hasSize(6);
        assertThat(sk.get("items").findValuesAsText("direction"))
                .containsExactly("ABOVE", "BELOW", "BELOW", "SIMILAR", "ABOVE", "SIMILAR");
        assertThat(sk.get("items").get(4).get("label").asText()).isEqualTo("다리 길이(샅높이)");
        assertThat(sk.get("items").get(1).get("deviationPercent").asDouble()).isEqualTo(-4.1);
        assertThat(sk.get("summary").get("shoulderDeviation").asDouble()).isEqualTo(0.04);
        assertThat(sk.get("summary").get("trunkDeviation").asDouble()).isEqualTo(-0.031);
        assertThat(d6.get("profileInput").get("age").asInt()).isEqualTo(21);
        assertThat(d6.get("editableOptions").get("faceShapes").findValuesAsText("key"))
                .containsExactly("diamond", "heart", "oval", "rectangle", "round", "long");
        assertThat(d6.get("editableOptions").get("bodyTypes")).hasSize(4);

        // ⑦ 수동 보정: heart → diamond → TYPE-07
        JsonNode d7 = data(call(json(patch(BASE + "/" + sid + "/profile"), "{\"faceShape\":\"diamond\"}"), 200));
        assertThat(texts(d7.get("changedFields"))).containsExactly("faceShape");
        assertThat(d7.get("nextStep").asText()).isEqualTo("RESULT_READY");
        assertThat(d7.get("result").get("type").get("typeCode").asText()).isEqualTo("TYPE-07");
        assertThat(d7.get("result").get("basis").get("faceSource").asText()).isEqualTo("MANUAL");
        assertThat(d7.get("result").get("basis").get("bodySource").asText()).isEqualTo("AI");
        assertThat(d7.get("result").get("hairStyles").get(0).get("reasonCodes").get(0).asText()).isEqualTo("FACE_DIAMOND_PARTED_STYLE");
        // 같은 값을 다시 보내면 changedFields = []
        JsonNode d7b = data(call(json(patch(BASE + "/" + sid + "/profile"), "{\"faceShape\":\"diamond\"}"), 200));
        assertThat(d7b.get("changedFields")).isEmpty();
        // 보정 후 상세: status CORRECTED, AI 원본 보존
        JsonNode d6b = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200));
        assertThat(d6b.get("face").get("status").asText()).isEqualTo("CORRECTED");
        assertThat(d6b.get("face").get("result").get("key").asText()).isEqualTo("diamond");
        assertThat(d6b.get("face").get("aiResult").get("key").asText()).isEqualTo("heart");
        assertThat(d6b.get("face").get("description").asText()).startsWith("광대 라인이 또렷하고");

        // ⑧ 초기화 (멱등)
        JsonNode d8 = data(call(delete(BASE + "/" + sid), 200));
        assertThat(d8.get("deleted").asBoolean()).isTrue();
        assertThat(d8.get("alreadyDeleted").asBoolean()).isFalse();
        assertThat(d8.get("deletedPhotoCount").asInt()).isZero();
        JsonNode d8b = data(call(delete(BASE + "/" + sid), 200));
        assertThat(d8b.get("alreadyDeleted").asBoolean()).isTrue();
        assertError(call(get(BASE + "/" + sid + "/result"), 404), "SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("AI 없이 직접 선택(⑦)만으로도 결과 산출: status MANUAL, AI 부가 정보는 null/[]")
    void manualSelectionWithoutAi() throws Exception {
        String sid = profiledSession();
        JsonNode first = data(call(json(patch(BASE + "/" + sid + "/profile"), "{\"faceShape\":\"oval\"}"), 200));
        assertThat(first.get("result").isNull()).as("체형 미확정이면 result = null").isTrue();
        assertThat(first.get("nextStep").asText()).isEqualTo("BODY_ANALYSIS");

        JsonNode second = data(call(json(patch(BASE + "/" + sid + "/profile"), "{\"bodyType\":\"standard\",\"hairCurl\":\"STRAIGHT\"}"), 200));
        assertThat(texts(second.get("changedFields"))).containsExactly("bodyType", "hairCurl");
        assertThat(second.get("result").get("type").get("typeCode").asText()).isEqualTo("TYPE-03");

        JsonNode face = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200)).get("face");
        assertThat(face.get("status").asText()).isEqualTo("MANUAL");
        assertNullKey(face, "aiResult");
        assertNullKey(face, "aiReason");
        assertNullKey(face, "confidence");
        assertNullKey(face, "analyzedAt");
        assertThat(face.get("candidates")).isEmpty();
    }

    @Test
    @DisplayName("⑥ 미분석 세션도 200: status NOT_ANALYZED, 모든 값 null/[], profileInput null")
    void analysisDetailNotAnalyzed() throws Exception {
        String sid = newSession();
        JsonNode d = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200));
        for (String part : List.of("face", "body")) {
            JsonNode n = d.get(part);
            assertThat(n.get("status").asText()).isEqualTo("NOT_ANALYZED");
            for (String key : List.of("source", "result", "aiResult", "description", "confidence", "borderline", "analyzedAt")) {
                assertNullKey(n, key);
            }
            assertThat(n.get("candidates")).isEmpty();
            assertThat(n.get("warnings")).isEmpty();
            assertThat(n.get("features")).isEmpty();
        }
        assertNullKey(d.get("body"), "heightCmUsed");
        assertNullKey(d.get("body"), "compareToSizeKorea");
        assertNullKey(d, "profileInput");
    }

    @Test
    @DisplayName("② 몸무게 미입력 → weightKg 키는 존재하고 값은 null")
    void weightOmittedIsSerializedAsNull() throws Exception {
        String sid = newSession();
        JsonNode d = data(call(json(post(BASE + "/" + sid + "/profile-input"),
                "{\"age\":25,\"heightCm\":178,\"hairThickness\":\"MEDIUM\",\"hairCurl\":\"STRAIGHT\",\"hairDensity\":\"MEDIUM\"}"), 200));
        assertNullKey(d.get("profileInput"), "weightKg");
    }

    @Test
    @DisplayName("② AI 체형 분석 후 키 변경 → bodyAnalysisReset=true, 체형 미확정으로 되돌아감")
    void heightChangeResetsBody() throws Exception {
        String sid = profiledSession();
        call(face(sid), 200);
        call(body(sid).header("X-Mock-Body-Type", "rectangle"), 200);

        JsonNode same = data(call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006.replace("\"age\":21", "\"age\":22")), 200));
        assertThat(same.get("bodyAnalysisReset").asBoolean()).as("키가 같으면 초기화하지 않음").isFalse();

        JsonNode changed = data(call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006.replace("174", "180")), 200));
        assertThat(changed.get("bodyAnalysisReset").asBoolean()).isTrue();
        assertProgress(changed, true, true, false);
        assertThat(changed.get("nextStep").asText()).isEqualTo("BODY_ANALYSIS");
        assertThat(data(call(get(BASE + "/" + sid + "/analysis-detail"), 200)).get("body").get("status").asText())
                .isEqualTo("NOT_ANALYZED");
    }

    // ==================================================================================== Mock 헤더

    @Nested
    @DisplayName("Mock 헤더")
    class MockHeaders {

        @Test
        @DisplayName("X-Mock-Unusable (얼굴): 200 + usable=false, retakeRequired, progress 불변, 이전 결과 유지")
        void faceUnusable() throws Exception {
            String sid = profiledSession();
            call(face(sid).header("X-Mock-Face-Shape", "round"), 200);

            JsonNode d = data(call(face(sid).header("X-Mock-Unusable", "true"), 200));
            assertThat(d.get("usable").asBoolean()).isFalse();
            assertThat(d.get("retakeRequired").asBoolean()).isTrue();
            assertNullKey(d, "faceShape");
            assertNullKey(d, "confidence");
            assertNullKey(d, "borderline");
            assertThat(d.get("warnings")).hasSize(1);
            assertThat(d.get("warnings").get(0).get("code").asText()).isEqualTo("FACE_YAW_EXCEEDED");
            assertThat(d.get("warnings").get(0).get("severity").asText()).isEqualTo("RETAKE");
            assertThat(d.get("warnings").get(0).get("message").asText()).isEqualTo("고개를 좌우로 돌리지 말고 정면을 바라봐 주세요.");
            assertProgress(d, true, true, false);                                       // 이전 성공 결과(round) 유지
            JsonNode detail = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200)).get("face");
            assertThat(detail.get("result").get("key").asText()).isEqualTo("round");
        }

        @Test
        @DisplayName("X-Mock-Unusable (전신, 최초): progress 불변, nextStep 그대로")
        void bodyUnusable() throws Exception {
            String sid = profiledSession();
            call(face(sid), 200);
            JsonNode d = data(call(body(sid).header("X-Mock-Unusable", "true"), 200));
            assertThat(d.get("usable").asBoolean()).isFalse();
            assertNullKey(d, "bodyType");
            assertThat(d.get("heightCmUsed").asInt()).isEqualTo(174);
            assertThat(d.get("warnings").get(0).get("code").asText()).isEqualTo("BODY_LOW_VISIBILITY");
            assertProgress(d, true, true, false);
            assertThat(d.get("nextStep").asText()).isEqualTo("BODY_ANALYSIS");
        }

        @Test
        @DisplayName("X-Mock-Borderline: confidence 0.52 + FACE_BORDERLINE(INFO) 안내")
        void borderline() throws Exception {
            String sid = profiledSession();
            JsonNode d = data(call(face(sid).header("X-Mock-Borderline", "true"), 200));
            assertThat(d.get("usable").asBoolean()).isTrue();
            assertThat(d.get("confidence").asDouble()).isEqualTo(0.52);
            assertThat(d.get("borderline").asBoolean()).isTrue();
            assertThat(d.get("warnings").get(0).get("code").asText()).isEqualTo("FACE_BORDERLINE");
            assertThat(d.get("warnings").get(0).get("severity").asText()).isEqualTo("INFO");
            assertThat(d.get("faceShape").get("key").asText()).isEqualTo("oval");      // 헤더 없으면 기본 oval
        }

        @Test
        @DisplayName("X-Mock-Body-Minimal: 체형만 확정, 부가 정보는 키 유지 + null/[]")
        void bodyMinimal() throws Exception {
            String sid = profiledSession();
            call(face(sid), 200);
            JsonNode d = data(call(body(sid).header("X-Mock-Body-Minimal", "true").header("X-Mock-Body-Type", "large_rectangle"), 200));
            assertThat(d.get("usable").asBoolean()).isTrue();
            assertThat(d.get("bodyType").get("key").asText()).isEqualTo("large_rectangle");
            assertNullKey(d, "confidence");
            assertNullKey(d, "borderline");
            assertThat(d.get("nextStep").asText()).isEqualTo("RESULT_READY");

            JsonNode b = data(call(get(BASE + "/" + sid + "/analysis-detail"), 200)).get("body");
            assertThat(b.get("status").asText()).isEqualTo("ANALYZED");
            assertThat(b.get("result").get("label").asText()).isEqualTo("큰 사각 체형");
            assertThat(b.get("description").asText()).isNotBlank();
            assertNullKey(b, "confidence");
            assertNullKey(b, "borderline");
            assertNullKey(b, "compareToSizeKorea");
            assertThat(b.get("candidates")).isEmpty();
            assertThat(b.get("features")).isEmpty();
            assertThat(b.get("heightCmUsed").asInt()).isEqualTo(174);
            call(get(BASE + "/" + sid + "/result"), 200);                              // 최소 응답으로도 결과 산출 가능
        }

        @Test
        @DisplayName("잘못된 Mock 헤더 값 → 400 INVALID_REQUEST + details")
        void invalidMockHeader() throws Exception {
            String sid = profiledSession();
            JsonNode err = assertError(call(face(sid).header("X-Mock-Face-Shape", "triangle"), 400), "INVALID_REQUEST");
            assertThat(err.get("details").get(0).get("field").asText()).isEqualTo("X-Mock-Face-Shape");
            assertError(call(face(sid).header("X-Mock-Delay-Ms", "99999"), 400), "INVALID_REQUEST");
        }
    }

    // ==================================================================================== 예외

    @Nested
    @DisplayName("예외 응답")
    class Errors {

        @Test
        @DisplayName("PHOTO_CONSENT_REQUIRED: false · 누락 · 본문 없음")
        void photoConsentRequired() throws Exception {
            JsonNode err = assertError(call(json(post(BASE + "/guest"), "{\"photoConsent\":false}"), 400), "PHOTO_CONSENT_REQUIRED");
            assertThat(err.get("message").asText()).isEqualTo("사진 분석 동의가 필요해요.");
            assertError(call(json(post(BASE + "/guest"), "{}"), 400), "PHOTO_CONSENT_REQUIRED");
            assertError(call(post(BASE + "/guest"), 400), "PHOTO_CONSENT_REQUIRED");
        }

        @Test
        @DisplayName("INVALID_REQUEST: api-spec.md ② 예시 (age 12 + hairCurl WAVY) → details 2건, 선언 순서")
        void invalidProfile() throws Exception {
            String sid = newSession();
            JsonNode root = call(json(post(BASE + "/" + sid + "/profile-input"),
                    "{\"age\":12,\"heightCm\":174,\"hairThickness\":\"THIN\",\"hairCurl\":\"WAVY\",\"hairDensity\":\"MEDIUM\"}"), 400);
            assertThat(root.get("success").asBoolean()).isFalse();
            assertThat(root.has("data")).as("실패 응답에는 data 키가 없다").isFalse();
            JsonNode err = root.get("error");
            assertThat(err.get("status").asInt()).isEqualTo(400);
            assertThat(err.get("code").asText()).isEqualTo("INVALID_REQUEST");
            assertThat(err.get("message").asText()).isEqualTo("입력값을 확인해 주세요.");
            assertNullKey(err, "meta");
            assertThat(err.get("path").asText()).isEqualTo(BASE + "/" + sid + "/profile-input");
            assertThat(err.get("timestamp").asText()).endsWith("+09:00");
            JsonNode details = err.get("details");
            assertThat(details).hasSize(2);
            assertThat(details.get(0).get("field").asText()).isEqualTo("age");
            assertThat(details.get(0).get("reason").asText()).isEqualTo("14 이상 80 이하의 정수로 입력해 주세요.");
            assertThat(details.get(0).get("rejectedValue").asInt()).isEqualTo(12);
            assertThat(details.get(1).get("field").asText()).isEqualTo("hairCurl");
            assertThat(details.get(1).get("reason").asText()).isEqualTo("STRAIGHT, SEMI_CURLY, CURLY 중 하나여야 합니다.");
            assertThat(details.get(1).get("rejectedValue").asText()).isEqualTo("WAVY");
        }

        @Test
        @DisplayName("INVALID_REQUEST: 필수 누락 · 타입 오류 · 소수 · JSON 문법 오류 · deviceLabel 50자 초과")
        void invalidRequestVariants() throws Exception {
            String sid = newSession();
            JsonNode missing = assertError(call(json(post(BASE + "/" + sid + "/profile-input"), "{\"age\":21}"), 400), "INVALID_REQUEST");
            assertThat(missing.get("details").findValuesAsText("field"))
                    .containsExactly("heightCm", "hairThickness", "hairCurl", "hairDensity");

            JsonNode typed = assertError(call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006.replace("21", "\"abc\"")), 400), "INVALID_REQUEST");
            assertThat(typed.get("details").get(0).get("field").asText()).isEqualTo("age");
            assertThat(typed.get("details").get(0).get("reason").asText()).isEqualTo("정수로 입력해 주세요.");

            assertError(call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006.replace("21", "21.5")), 400), "INVALID_REQUEST");
            assertError(call(json(post(BASE + "/" + sid + "/profile-input"), "{\"age\":"), 400), "INVALID_REQUEST");

            JsonNode label = assertError(call(json(post(BASE + "/guest"), "{\"photoConsent\":true,\"deviceLabel\":\"" + "x".repeat(51) + "\"}"), 400), "INVALID_REQUEST");
            assertThat(label.get("details").get(0).get("field").asText()).isEqualTo("deviceLabel");
        }

        @Test
        @DisplayName("⑦ INVALID_REQUEST: 모든 필드 누락 / 잘못된 얼굴형 Key(대문자)")
        void invalidPatch() throws Exception {
            String sid = profiledSession();
            JsonNode empty = assertError(call(json(patch(BASE + "/" + sid + "/profile"), "{}"), 400), "INVALID_REQUEST");
            assertThat(empty.get("message").asText()).isEqualTo("수정할 항목을 1개 이상 보내 주세요.");
            assertThat(empty.get("details")).isEmpty();
            JsonNode badKey = assertError(call(json(patch(BASE + "/" + sid + "/profile"), "{\"faceShape\":\"HEART\"}"), 400), "INVALID_REQUEST");
            assertThat(badKey.get("details").get(0).get("reason").asText())
                    .isEqualTo("diamond, heart, oval, rectangle, round, long 중 하나여야 합니다.");
        }

        @Test
        @DisplayName("SESSION_NOT_FOUND / SESSION_EXPIRED, 만료 세션 삭제는 alreadyDeleted=true, 스케줄러가 정리")
        void notFoundAndExpired() throws Exception {
            assertError(call(get(BASE + "/" + UUID.randomUUID() + "/result"), 404), "SESSION_NOT_FOUND");

            String sid = profiledSession();
            GuestSession session = repository.findById(sid).orElseThrow();
            session.renewExpiry(Instant.now().minusSeconds(60));
            repository.save(session);

            JsonNode err = assertError(call(get(BASE + "/" + sid + "/analysis-detail"), 410), "SESSION_EXPIRED");
            assertThat(err.get("message").asText()).isEqualTo("체험 시간이 지나 세션이 종료되었어요. 처음부터 다시 시작해 주세요.");
            assertError(call(face(sid), 410), "SESSION_EXPIRED");
            assertError(call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006), 410), "SESSION_EXPIRED");

            assertThat(cleanupScheduler.cleanupExpiredSessions()).isGreaterThanOrEqualTo(1);
            assertThat(repository.findById(sid)).isEmpty();
            assertThat(data(call(delete(BASE + "/" + sid), 200)).get("alreadyDeleted").asBoolean()).isTrue();
        }

        @Test
        @DisplayName("PROFILE_INPUT_REQUIRED: 기본 정보 전 전신 분석 · 결과 조회 · 모질 보정")
        void profileInputRequired() throws Exception {
            String sid = newSession();
            assertError(call(body(sid), 409), "PROFILE_INPUT_REQUIRED");
            assertError(call(get(BASE + "/" + sid + "/result"), 409), "PROFILE_INPUT_REQUIRED");
            assertError(call(json(patch(BASE + "/" + sid + "/profile"), "{\"hairCurl\":\"CURLY\"}"), 409), "PROFILE_INPUT_REQUIRED");
            call(face(sid), 200);                                                    // 얼굴 분석은 기본 정보 전에도 가능
        }

        @Test
        @DisplayName("ANALYSIS_INCOMPLETE: meta.missing / meta.nextStep")
        void analysisIncomplete() throws Exception {
            String sid = profiledSession();
            JsonNode both = assertError(call(get(BASE + "/" + sid + "/result"), 409), "ANALYSIS_INCOMPLETE");
            assertThat(texts(both.get("meta").get("missing"))).containsExactly("FACE", "BODY");
            assertThat(both.get("meta").get("nextStep").asText()).isEqualTo("FACE_ANALYSIS");

            call(face(sid), 200);
            JsonNode bodyOnly = assertError(call(get(BASE + "/" + sid + "/result"), 409), "ANALYSIS_INCOMPLETE");
            assertThat(texts(bodyOnly.get("meta").get("missing"))).containsExactly("BODY");
            assertThat(bodyOnly.get("meta").get("nextStep").asText()).isEqualTo("BODY_ANALYSIS");
            assertThat(bodyOnly.get("message").asText()).isEqualTo("얼굴과 전신 분석이 모두 필요해요.");
        }

        @Test
        @DisplayName("사진 검증: UNSUPPORTED_IMAGE_TYPE(415) · IMAGE_REQUIRED(400) · IMAGE_TOO_LARGE(413)")
        void imageValidation() throws Exception {
            String sid = profiledSession();
            MockMultipartFile gif = new MockMultipartFile("image", "face.gif", "image/gif", new byte[]{1, 2, 3});
            assertError(call(multipart(BASE + "/" + sid + "/analyze/face").file(gif), 415), "UNSUPPORTED_IMAGE_TYPE");

            assertError(call(multipart(BASE + "/" + sid + "/analyze/face"), 400), "IMAGE_REQUIRED");
            assertError(call(post(BASE + "/" + sid + "/analyze/face"), 400), "IMAGE_REQUIRED");
            MockMultipartFile empty = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[0]);
            assertError(call(multipart(BASE + "/" + sid + "/analyze/face").file(empty), 400), "IMAGE_REQUIRED");

            MockMultipartFile big = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);
            JsonNode tooLarge = assertError(call(multipart(BASE + "/" + sid + "/analyze/face").file(big), 413), "IMAGE_TOO_LARGE");
            assertThat(tooLarge.get("message").asText()).isEqualTo("사진 용량은 10MB 이하만 가능해요.");

            MockMultipartFile png = new MockMultipartFile("image", "face.png", "image/png", new byte[]{1});
            call(multipart(BASE + "/" + sid + "/analyze/face").file(png), 200);       // PNG 허용
        }

        @Test
        @DisplayName("ANALYSIS_IN_PROGRESS: 같은 세션 분석 진행 중 재요청 → 409")
        void analysisInProgress() throws Exception {
            String sid = profiledSession();
            CompletableFuture<MvcResult> slow = CompletableFuture.supplyAsync(() -> {
                try {
                    return mvc.perform(face(sid).header("X-Mock-Delay-Ms", "1500")).andReturn();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            long deadline = System.currentTimeMillis() + 3000;
            while (!inProgressRegistry.isInProgress(sid) && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(inProgressRegistry.isInProgress(sid)).isTrue();
            assertError(call(face(sid), 409), "ANALYSIS_IN_PROGRESS");
            assertThat(slow.get().getResponse().getStatus()).isEqualTo(200);
            assertThat(inProgressRegistry.isInProgress(sid)).as("완료 후 잠금 해제").isFalse();
            call(face(sid), 200);
        }

        @Test
        @DisplayName("지원하지 않는 메서드 → 405 + 공통 에러 포맷")
        void methodNotAllowed() throws Exception {
            JsonNode err = assertError(call(get(BASE + "/guest"), 405), "INVALID_REQUEST");
            assertThat(err.get("status").asInt()).isEqualTo(405);
        }
    }

    // ==================================================================================== helpers

    private String newSession() throws Exception {
        return data(call(json(post(BASE + "/guest"), "{\"photoConsent\":true}"), 201)).get("sessionId").asText();
    }

    private String profiledSession() throws Exception {
        String sid = newSession();
        call(json(post(BASE + "/" + sid + "/profile-input"), PROFILE_S006), 200);
        return sid;
    }

    private static MockMultipartHttpServletRequestBuilder face(String sid) {
        return multipart(BASE + "/" + sid + "/analyze/face").file(jpeg("face.jpg"));
    }

    private static MockMultipartHttpServletRequestBuilder body(String sid) {
        return multipart(BASE + "/" + sid + "/analyze/body").file(jpeg("body.jpg"));
    }

    private static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("image", name, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as("HTTP status — body: %s", content).isEqualTo(expectedStatus);
        return objectMapper.readTree(content);
    }

    private static JsonNode data(JsonNode root) {
        assertThat(root.get("success").asBoolean()).isTrue();
        assertThat(root.has("error")).isFalse();
        return root.get("data");
    }

    private static JsonNode assertError(JsonNode root, String code) {
        assertThat(root.get("success").asBoolean()).isFalse();
        assertThat(root.has("data")).isFalse();
        JsonNode err = root.get("error");
        assertThat(err.get("code").asText()).isEqualTo(code);
        assertThat(err.has("details")).isTrue();
        assertThat(err.has("meta")).isTrue();
        return err;
    }

    private static void assertProgress(JsonNode data, boolean profile, boolean face, boolean body) {
        JsonNode p = data.get("progress");
        assertThat(List.of(p.get("profileInput").asBoolean(), p.get("faceAnalysis").asBoolean(), p.get("bodyAnalysis").asBoolean()))
                .containsExactly(profile, face, body);
    }

    private static void assertNullKey(JsonNode node, String key) {
        assertThat(node.has(key)).as("키 '%s'는 생략되지 않아야 함", key).isTrue();
        assertThat(node.get(key).isNull()).as("키 '%s'의 값은 null", key).isTrue();
    }

    private static List<String> codes(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.get("code").asText()));
        return list;
    }

    private static List<String> texts(JsonNode array) {
        List<String> list = new ArrayList<>();
        array.forEach(n -> list.add(n.asText()));
        return list;
    }
}
