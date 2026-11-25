package com.moda.seed.dto;

import java.util.List;

/** {@code seed/analysis-labels.json} (docs/api-spec.md 3.9, 부록 A.4, A.5). */
public record AnalysisLabelsSeed(
        String ruleVersion,
        List<FeatureLabelSeed> features,
        SizeKoreaSeed sizeKorea,
        List<WarningSeed> warnings,
        UndefinedWarningSeed undefinedWarning) {

    /**
     * @param target            {@code FACE} 또는 {@code BODY}
     * @param aiValueMultiplier AI 원시값에 곱할 배율 (체형 편차: 비율 → percent 이므로 100)
     */
    public record FeatureLabelSeed(
            String key,
            String label,
            String unit,
            String description,
            String target,
            Integer aiValueMultiplier,
            String note) {
    }

    public record SizeKoreaSeed(
            String reference,
            String referenceLabel,
            List<SizeKoreaItemSeed> items) {
    }

    /** @param sizeKoreaItem Size Korea 원본 항목명 (예: {@code (318) 위팔사이너비}) */
    public record SizeKoreaItemSeed(
            String key,
            String sizeKoreaItem,
            String label) {
    }

    /**
     * @param target   {@code FACE} / {@code BODY} / {@code COMMON}
     * @param criteria 발생 기준 ({@code config.py})
     * @param message  앱 표시 문구
     */
    public record WarningSeed(
            String code,
            String target,
            String severity,
            String criteria,
            String message) {
    }

    /** 정의되지 않은 경고 코드 수신 시 사용할 기본값. */
    public record UndefinedWarningSeed(
            String severity,
            String message) {
    }
}
