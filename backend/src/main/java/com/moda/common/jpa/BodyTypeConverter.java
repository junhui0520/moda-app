package com.moda.common.jpa;

import com.moda.common.enums.BodyType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link BodyType} ↔ 소문자 Key 컬럼 (예: {@code small_inv_triangle}). 식별자(@Id)가 아닌 컬럼에 자동 적용됩니다. */
@Converter(autoApply = true)
public class BodyTypeConverter implements AttributeConverter<BodyType, String> {

    @Override
    public String convertToDatabaseColumn(BodyType attribute) {
        return attribute == null ? null : attribute.key();
    }

    @Override
    public BodyType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BodyType.fromKey(dbData);
    }
}
