package com.moda.ai;

/**
 * Mock 모드 테스트 헤더 원본값 (docs/api-spec.md 1.5). {@code moda.ai.mode=external}이면 무시됩니다.
 * 값 해석·검증은 {@link MockAnalysisClient}가 담당합니다.
 */
public record AnalysisOptions(
        String mockFaceShape,
        String mockBodyType,
        String mockUnusable,
        String mockBorderline,
        String mockBodyMinimal,
        String mockDelayMs) {

    public static final String H_FACE_SHAPE = "X-Mock-Face-Shape";
    public static final String H_BODY_TYPE = "X-Mock-Body-Type";
    public static final String H_UNUSABLE = "X-Mock-Unusable";
    public static final String H_BORDERLINE = "X-Mock-Borderline";
    public static final String H_BODY_MINIMAL = "X-Mock-Body-Minimal";
    public static final String H_DELAY_MS = "X-Mock-Delay-Ms";

    public static AnalysisOptions none() {
        return new AnalysisOptions(null, null, null, null, null, null);
    }
}
