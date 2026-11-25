package com.moda.seed;

/** 시드 JSON 파일 5종 (경로: {@code backend/src/main/resources/seed/}). */
public enum SeedFile {
    TYPE_DEFINITIONS("type-definitions.json"),
    CATALOG("catalog.json"),
    HAIR_RULES("hair-rules.json"),
    FASHION_RULES("fashion-rules.json"),
    ANALYSIS_LABELS("analysis-labels.json");

    private final String fileName;

    SeedFile(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }
}
