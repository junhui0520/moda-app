package com.moda.seed.dto;

import java.util.List;

/** {@code seed/type-definitions.json} (docs/style-rules.md 5.2, 5.3). */
public record TypeDefinitionsSeed(
        String ruleVersion,
        String headlineTemplate,
        List<TypeSeed> types) {

    public record TypeSeed(
            String typeCode,
            String bodyType,
            String faceShape,
            String typeName,
            Boolean nameConfirmed,
            String description,
            List<String> stylePoints,
            Integer sortOrder) {
    }
}
