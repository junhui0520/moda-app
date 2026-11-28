package com.moda.seed.dto;

import java.util.List;
import java.util.Map;

/**
 * {@code seed/hair-rules.json} (style-rules.md 3.1~3.4) / {@code seed/fashion-rules.json} (4.1~4.4) 공통 구조.
 *
 * @param domain         {@code HAIR} 또는 {@code FASHION}
 * @param baseFactor     1차 기준: 헤어 {@code faceShape}, 패션 {@code bodyType}
 * @param styleOrder     스타일 코드 선언 순서 (동점 시 정렬 3순위)
 * @param baseScores     기본 점수표: {@code baseKey(얼굴형/체형) → styleCode → score}
 * @param baseRationale  기본 점수표 설계 근거 요약: {@code baseKey → 방향}
 * @param adjustments    보정표 행: 헤어 모질 9행, 패션 구간 11행
 */
public record RuleSeed(
        String ruleVersion,
        String domain,
        String baseFactor,
        List<String> styleOrder,
        ScoreRange baseScoreRange,
        Map<String, Map<String, Integer>> baseScores,
        Map<String, String> baseRationale,
        List<AdjustmentRowSeed> adjustments,
        List<BaseReasonSeed> baseReasons,
        List<GenericReasonSeed> genericReasons,
        List<AdjustmentReasonSeed> adjustmentReasons) {

    public record ScoreRange(Integer min, Integer max) {
    }

    /**
     * @param factor 헤어: {@code hairThickness|hairCurl|hairDensity}, 패션: {@code bmi|height|age}
     * @param value  헤어: 모질 Enum 값, 패션: 구간명 (예: {@code BMI_LOW})
     * @param deltas {@code styleCode → delta}
     * @param note   헤어: 보정 근거, 패션: 구간 범위
     */
    public record AdjustmentRowSeed(
            String factor,
            String value,
            Map<String, Integer> deltas,
            String note) {
    }

    /** 기본 점수 전용 문구 (기본 점수 4점 이상 조합). */
    public record BaseReasonSeed(
            String reasonCode,
            String baseKey,
            String styleCode,
            String text) {
    }

    /** 기본 점수 일반 문구 (기본 점수 3점 이하 조합). */
    public record GenericReasonSeed(
            String reasonCode,
            String styleCode,
            String text) {
    }

    /** 양수 보정 근거 문구. */
    public record AdjustmentReasonSeed(
            String reasonCode,
            String factor,
            String value,
            String styleCode,
            String text) {
    }
}
