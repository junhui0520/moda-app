package com.moda.ai.model;

import com.moda.common.enums.WarningSeverity;
import java.util.List;

/**
 * AI 분석 값 객체. AI 응답을 정규화한 형태이며 세션 엔티티에 JSON으로 그대로 저장됩니다.
 * 화면 라벨·문구는 저장하지 않고 응답 시점에 시드에서 붙입니다 (R-07).
 */
public final class AnalysisValues {

    private AnalysisValues() {
    }

    /** 후보: 얼굴형/체형 Key + 확률(0~1, 소수 셋째 자리). */
    public record CandidateValue(String key, double probability) {
    }

    /** AI 측정값 원시값 (체형 편차는 비율 그대로, 응답 시 라벨의 배율로 percent 변환). */
    public record FeatureValue(String key, double value) {
    }

    /** 정규화된 경고 (A.5 규칙 적용 후 severity). */
    public record WarningValue(String code, WarningSeverity severity) {
    }

    /** Size Korea 비교 (체형 전용). */
    public record SizeKoreaValue(String reference, Integer heightCm, List<SizeKoreaItemValue> items, SizeKoreaSummaryValue summary) {
    }

    public record SizeKoreaItemValue(String key, int userMm, int refMm, double deviation) {
    }

    public record SizeKoreaSummaryValue(double shoulderDeviation, double trunkDeviation) {
    }
}
