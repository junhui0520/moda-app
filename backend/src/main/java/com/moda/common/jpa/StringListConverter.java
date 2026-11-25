package com.moda.common.jpa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * {@code List<String>} ↔ JSON 문자열 컬럼 변환.
 * <p>H2·MySQL 모두 일반 문자열(VARCHAR) 컬럼에 {@code ["a","b"]} 형태로 저장하므로 DB 고유 JSON 타입에 의존하지 않습니다.
 * 순서가 보존되므로 {@code itemKeywords}·{@code stylePoints}의 고정 순서가 유지됩니다.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("List<String> → JSON 변환 실패", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(MAPPER.readValue(dbData, TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON → List<String> 변환 실패: " + dbData, e);
        }
    }
}
