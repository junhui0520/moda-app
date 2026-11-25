package com.moda.ai.model;

import com.moda.ai.model.AnalysisValues.CandidateValue;
import com.moda.ai.model.AnalysisValues.FeatureValue;
import com.moda.common.enums.FaceShape;
import java.util.List;

/**
 * 얼굴 분석 결과 (AI 응답 A.2를 검증·정규화한 값).
 *
 * @param warningCodes AI가 보낸 원본 경고 코드 (severity 정규화는 세션 서비스에서 수행)
 * @param reason       AI {@code v8} 평균 대비 특징 요약 문장 (선택, 없으면 {@code null})
 */
public record FaceAnalysisResult(
        boolean usable,
        FaceShape faceShape,
        Double confidence,
        Boolean borderline,
        String reason,
        List<CandidateValue> candidates,
        List<String> warningCodes,
        List<FeatureValue> features,
        String modelVersion) {

    public FaceAnalysisResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
        features = features == null ? List.of() : List.copyOf(features);
    }
}
