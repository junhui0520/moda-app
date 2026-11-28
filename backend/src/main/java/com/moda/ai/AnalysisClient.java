package com.moda.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.moda.ai.model.BodyAnalysisResult;
import com.moda.ai.model.FaceAnalysisResult;

/**
 * 얼굴·체형 분석 어댑터. {@code moda.ai.mode}로 구현체를 선택합니다 ({@link AnalysisClientConfig}).
 * <ul>
 *   <li>{@code mock}: {@link MockAnalysisClient} — Mock 헤더로 결과 지정</li>
 *   <li>{@code external}: {@link ExternalAiAnalysisClient} — AI 서버 호출 (api-spec.md 부록 A)</li>
 * </ul>
 * 오류는 {@link com.moda.global.error.BusinessException}으로 던집니다:
 * {@code 502 AI_BAD_RESPONSE} / {@code 503 AI_SERVER_UNAVAILABLE} / {@code 504 AI_TIMEOUT} (A.6).
 * 사진이 판정 불가인 경우는 오류가 아니라 {@code usable=false} 결과입니다.
 */
public interface AnalysisClient {

    FaceAnalysisResult analyzeFace(AnalysisImage image, AnalysisOptions options);

    BodyAnalysisResult analyzeBody(AnalysisImage image, int heightCm, AnalysisOptions options);

    /**
     * ③-L 얼굴 실시간 분석 (ai/LIVE_INTEGRATION.md). 휴대폰 측정 페이지가 잰 값만 받으며 사진은 없습니다.
     *
     * @param measurement 측정 페이지 {@code payload} ({@code rounds}, {@code quality}) — 그 외 필드는 AI로 전달하지 않음
     */
    FaceAnalysisResult analyzeFaceLive(JsonNode measurement, String requestId, AnalysisOptions options);

    /** ④-L 체형 실시간 분석. {@code heightCm}은 세션 값을 서버가 붙입니다 (기존 ④와 같음). */
    BodyAnalysisResult analyzeBodyLive(JsonNode measurement, int heightCm, String requestId, AnalysisOptions options);
}
