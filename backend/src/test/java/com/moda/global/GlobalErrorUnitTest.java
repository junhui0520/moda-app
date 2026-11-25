package com.moda.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.global.error.ApiError;
import com.moda.global.error.BusinessException;
import com.moda.global.error.ErrorCode;
import com.moda.global.error.GlobalExceptionHandler;
import com.moda.global.response.ApiResponse;
import com.moda.global.time.KstTime;
import com.moda.session.service.AnalysisInProgressRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 공통 응답 직렬화 · 에러 코드표 · 예외 변환 · 분석 진행 잠금 단위 테스트. */
@JsonTest
class GlobalErrorUnitTest {

    @Autowired ObjectMapper objectMapper;

    record Sample(String name, Integer weightKg, List<String> items) {
    }

    @Test
    @DisplayName("ErrorCode 17종: api-spec.md 4.1과 HTTP 상태 일치")
    void errorCodeTable() {
        assertThat(ErrorCode.values()).hasSize(17);
        assertThat(Arrays.stream(ErrorCode.values()).map(c -> c.name() + ":" + c.status().value())).containsExactlyInAnyOrder(
                "INVALID_REQUEST:400", "PHOTO_CONSENT_REQUIRED:400", "IMAGE_REQUIRED:400", "SESSION_NOT_FOUND:404",
                "PROFILE_INPUT_REQUIRED:409", "ANALYSIS_INCOMPLETE:409", "ANALYSIS_IN_PROGRESS:409", "SESSION_EXPIRED:410",
                "IMAGE_TOO_LARGE:413", "UNSUPPORTED_IMAGE_TYPE:415", "CHAT_TURN_LIMIT_EXCEEDED:429", "INTERNAL_ERROR:500",
                "AI_BAD_RESPONSE:502", "AI_SERVER_UNAVAILABLE:503", "CHAT_SERVER_UNAVAILABLE:503", "AI_TIMEOUT:504", "CHAT_TIMEOUT:504");
    }

    @Test
    @DisplayName("성공 Envelope: error 키 없음, data 내부 null 필드는 null로 유지")
    void successEnvelope() throws Exception {
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.success(new Sample("a", null, List.of()))));
        assertThat(root.fieldNames()).toIterable().containsExactly("success", "data");
        assertThat(root.get("data").has("weightKg")).isTrue();
        assertThat(root.get("data").get("weightKg").isNull()).isTrue();
        assertThat(root.get("data").get("items").isArray()).isTrue();
    }

    @Test
    @DisplayName("실패 Envelope: data 키 없음, meta null 유지, timestamp는 +09:00")
    void failureEnvelope() throws Exception {
        ApiError error = new ApiError(400, ErrorCode.INVALID_REQUEST, "입력값을 확인해 주세요.", null, null, "/x",
                KstTime.toOffset(Instant.parse("2026-11-19T12:31:05Z")));
        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(ApiResponse.failure(error)));
        assertThat(root.fieldNames()).toIterable().containsExactly("success", "error");
        assertThat(root.get("error").get("details").isArray()).isTrue();
        assertThat(root.get("error").get("meta").isNull()).isTrue();
        assertThat(root.get("error").get("timestamp").asText()).isEqualTo("2026-11-19T21:31:05+09:00");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException → 413 IMAGE_TOO_LARGE")
    void maxUploadSize() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(Clock.system(KstTime.ZONE));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/sessions/x/analyze/face");
        ResponseEntity<ApiResponse<Void>> res = handler.handleMaxUpload(new MaxUploadSizeExceededException(10L * 1024 * 1024), request);
        assertThat(res.getStatusCode().value()).isEqualTo(413);
        assertThat(res.getBody().error().code()).isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
        assertThat(res.getBody().error().path()).isEqualTo("/api/v1/sessions/x/analyze/face");
    }

    @Test
    @DisplayName("BusinessException.withMeta → meta 전달")
    void businessMeta() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(Clock.system(KstTime.ZONE));
        ResponseEntity<ApiResponse<Void>> res = handler.handleBusiness(
                BusinessException.withMeta(ErrorCode.ANALYSIS_INCOMPLETE, Map.of("missing", List.of("BODY"))),
                new MockHttpServletRequest("GET", "/r"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().error().meta()).containsEntry("missing", List.of("BODY"));
        assertThat(res.getBody().error().message()).isEqualTo("얼굴과 전신 분석이 모두 필요해요.");
    }

    @Test
    @DisplayName("분석 진행 잠금: 중복 요청 409, 해제·초기화 시 사진 바이트 0으로 폐기")
    void inProgressRegistry() {
        AnalysisInProgressRegistry registry = new AnalysisInProgressRegistry();
        byte[] photo = {1, 2, 3};
        AnalysisInProgressRegistry.InFlight token = registry.acquire("s1", photo);
        byte[] second = {9, 9};
        assertThatThrownBy(() -> registry.acquire("s1", second))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_IN_PROGRESS));
        assertThat(second).containsOnly(0);                                          // 거절된 요청의 사진도 즉시 폐기
        registry.release(token);
        assertThat(photo).containsOnly(0);
        assertThat(registry.isInProgress("s1")).isFalse();

        byte[] other = {7, 7};
        registry.acquire("s2", other);
        assertThat(registry.discard("s2")).isEqualTo(1);                             // ⑧ 분석 도중 초기화
        assertThat(other).containsOnly(0);
        assertThat(registry.discard("s2")).isZero();
    }
}
