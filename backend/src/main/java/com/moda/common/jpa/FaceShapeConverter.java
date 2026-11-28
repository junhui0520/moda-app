package com.moda.common.jpa;

import com.moda.common.enums.FaceShape;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link FaceShape} ↔ 소문자 Key 컬럼 (예: {@code heart}). 식별자(@Id)가 아닌 컬럼에 자동 적용됩니다. */
@Converter(autoApply = true)
public class FaceShapeConverter implements AttributeConverter<FaceShape, String> {

    @Override
    public String convertToDatabaseColumn(FaceShape attribute) {
        return attribute == null ? null : attribute.key();
    }

    @Override
    public FaceShape convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FaceShape.fromKey(dbData);
    }
}
