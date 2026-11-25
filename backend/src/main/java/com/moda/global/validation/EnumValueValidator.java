package com.moda.global.validation;

import com.moda.common.enums.KeyedEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;

public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private List<String> allowed;
    private String message;

    @Override
    public void initialize(EnumValue annotation) {
        this.allowed = allowedValues(annotation.enumClass());
        this.message = String.join(", ", allowed) + " 중 하나여야 합니다.";
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || allowed.contains(value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }

    public static List<String> allowedValues(Class<? extends Enum<?>> enumClass) {
        return Arrays.stream(enumClass.getEnumConstants())
                .map(e -> e instanceof KeyedEnum keyed ? keyed.key() : e.name())
                .toList();
    }
}
