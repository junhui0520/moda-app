package com.moda.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 앞뒤 공백을 제거(trim)한 뒤의 글자 수(코드 포인트 기준)가 {@code min}~{@code max}인지 검증합니다.
 * {@code null}·공백·줄바꿈만 있는 문자열은 실패입니다. (api-spec.md ⑩ {@code message}: trim 후 1~500자, 공백 불가)
 */
@Documented
@Constraint(validatedBy = TrimmedLength.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TrimmedLength {

    int min() default 1;

    int max();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TrimmedLength, String> {

        private int min;
        private int max;

        @Override
        public void initialize(TrimmedLength annotation) {
            this.min = annotation.min();
            this.max = annotation.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return false;
            }
            String trimmed = value.strip();
            int length = trimmed.codePointCount(0, trimmed.length());
            return length >= min && length <= max;
        }
    }
}
