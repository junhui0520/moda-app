package com.moda.seed.dto;

/** 시드 JSON 5종을 한 번에 담는 묶음. 검증·적재의 단위입니다. */
public record SeedBundle(
        TypeDefinitionsSeed typeDefinitions,
        CatalogSeed catalog,
        RuleSeed hairRules,
        RuleSeed fashionRules,
        AnalysisLabelsSeed analysisLabels) {
}
