package com.moda.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열이 지정한 Enum 값 중 하나인지 검증합니다. {@code null}은 통과(필수 여부는 {@code @NotNull}로 별도 지정).
 * <p>{@link com.moda.common.enums.KeyedEnum}(얼굴형·체형)은 소문자 Key, 그 외는 상수 이름과 비교합니다.
 * <p>Enum 필드를 문자열로 받으면 Jackson 변환 실패로 검증이 중단되지 않아, 다른 필드 오류와 함께 {@code details[]}에 모두 담깁니다
 * (api-spec.md ② 검증 실패 예: {@code age} + {@code hairCurl}).
 * 오류 문구: {@code "STRAIGHT, SEMI_CURLY, CURLY 중 하나여야 합니다."}
 */
@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {

    Class<? extends Enum<?>> enumClass();

    String message() default "허용되지 않는 값입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
