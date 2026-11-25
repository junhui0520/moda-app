package com.moda.ai.model;

import com.moda.ai.model.AnalysisValues.CandidateValue;
import com.moda.ai.model.AnalysisValues.FeatureValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaValue;
import com.moda.common.enums.BodyType;
import java.util.List;

/**
 * 체형 분석 결과 (AI 응답 A.3을 검증·정규화한 값). 필수는 {@code usable}·{@code bodyType}·{@code warningCodes}뿐이며
 * 나머지는 AI(유료 API)가 주지 않으면 {@code null}/{@code []}입니다 (A.4).
 */
public record BodyAnalysisResult(
        boolean usable,
        BodyType bodyType,
        Double confidence,
        Boolean borderline,
        List<CandidateValue> candidates,
        List<String> warningCodes,
        List<FeatureValue> features,
        SizeKoreaValue compareToSizeKorea,
        String modelVersion) {

    public BodyAnalysisResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
        features = features == null ? List.of() : List.copyOf(features);
    }
}
