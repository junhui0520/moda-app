package com.moda.session.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.ai.model.AnalysisValues.CandidateValue;
import com.moda.ai.model.AnalysisValues.FeatureValue;
import com.moda.ai.model.AnalysisValues.SizeKoreaValue;
import com.moda.ai.model.AnalysisValues.WarningValue;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * 분석 부가 정보(후보·경고·측정값·Size Korea 비교)를 JSON 문자열 컬럼으로 저장하는 변환기.
 * H2·MySQL 모두 일반 문자열(긴 VARCHAR/TEXT) 컬럼을 사용합니다. 사진·랜드마크 좌표는 저장하지 않습니다 (R-01).
 */
public final class JsonColumnConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonColumnConverters() {
    }

    abstract static class Base<T> implements AttributeConverter<T, String> {

        private final TypeReference<T> type;
        private final T emptyValue;

        Base(TypeReference<T> type, T emptyValue) {
            this.type = type;
            this.emptyValue = emptyValue;
        }

        @Override
        public String convertToDatabaseColumn(T attribute) {
            if (attribute == null) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(attribute);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("JSON 컬럼 직렬화 실패", e);
            }
        }

        @Override
        public T convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) {
                return emptyValue;
            }
            try {
                return MAPPER.readValue(dbData, type);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("JSON 컬럼 역직렬화 실패: " + dbData, e);
            }
        }
    }

    @Converter
    public static class CandidateList extends Base<List<CandidateValue>> {
        public CandidateList() {
            super(new TypeReference<>() {
            }, List.of());
        }
    }

    @Converter
    public static class WarningList extends Base<List<WarningValue>> {
        public WarningList() {
            super(new TypeReference<>() {
            }, List.of());
        }
    }

    @Converter
    public static class FeatureList extends Base<List<FeatureValue>> {
        public FeatureList() {
            super(new TypeReference<>() {
            }, List.of());
        }
    }

    @Converter
    public static class SizeKorea extends Base<SizeKoreaValue> {
        public SizeKorea() {
            super(new TypeReference<>() {
            }, null);
        }
    }
}
