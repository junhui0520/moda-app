package com.moda.recommendation.dto;

import com.moda.common.enums.FashionStyleCode;
import com.moda.common.enums.HairStyleCode;
import java.util.List;

/** 추천 결과 응답 객체 (docs/api-spec.md 3.4 ~ 3.6, 3.11 TypeResult). */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /** 3.11 {@code TypeResult}. */
    public record TypeResult(
            String typeCode,
            String typeName,
            boolean nameConfirmed,
            String headline,
            String description,
            List<String> stylePoints) {
    }

    /** 3.4 {@code HairRecommendation}. {@code score}는 화면 표시 금지. */
    public record HairRecommendation(
            int rank,
            HairStyleCode code,
            String name,
            String description,
            String imageKey,
            int score,
            List<String> reasons,
            List<String> reasonCodes,
            ScoreBreakdown scoreBreakdown) {
    }

    /** 3.5 {@code FashionRecommendation} = HairRecommendation 필드 + {@code itemKeywords}. */
    public record FashionRecommendation(
            int rank,
            FashionStyleCode code,
            String name,
            String description,
            String imageKey,
            int score,
            List<String> reasons,
            List<String> reasonCodes,
            ScoreBreakdown scoreBreakdown,
            List<String> itemKeywords) {
    }

    /** 3.6 {@code ScoreBreakdown}: delta ≠ 0 인 보정만 포함. */
    public record ScoreBreakdown(int base, List<Adjustment> adjustments) {
    }

    /** 3.6 {@code Adjustment}. {@code factor=bmi}이면 {@code value}는 항상 {@code "HIDDEN"} (R-11). */
    public record Adjustment(String code, String factor, String value, int delta) {
    }
}
