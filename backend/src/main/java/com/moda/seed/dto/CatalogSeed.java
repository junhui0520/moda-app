package com.moda.seed.dto;

import java.util.List;

/** {@code seed/catalog.json} (docs/style-rules.md 1.1, 1.3, 1.5, 1.6, 6.1, 6.2). */
public record CatalogSeed(
        String ruleVersion,
        List<FaceShapeSeed> faceShapes,
        List<BodyTypeSeed> bodyTypes,
        List<HairStyleSeed> hairStyles,
        List<FashionStyleSeed> fashionStyles) {

    public record FaceShapeSeed(
            String key,
            String label,
            Integer faceIndex,
            String description) {
    }

    /**
     * @param aiInternalLabel AI 코드 내부 한글명 ({@code BODY_LABELS}) — <b>화면 표시 금지</b>, 참고용
     * @param ruleSummary     AI {@code BODY_RULES} 판정 기준 요약(임시) — 참고용
     */
    public record BodyTypeSeed(
            String key,
            String label,
            Integer bodyIndex,
            String description,
            String aiInternalLabel,
            String ruleSummary) {
    }

    public record HairStyleSeed(
            String code,
            String name,
            String description,
            String imageKey,
            Integer sortOrder) {
    }

    public record FashionStyleSeed(
            String code,
            String name,
            String description,
            List<String> itemKeywords,
            String imageKey,
            Integer sortOrder) {
    }
}
