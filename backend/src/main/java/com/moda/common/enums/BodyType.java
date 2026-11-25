package com.moda.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/**
 * 체형 4종 (docs/style-rules.md 1.3).
 * <p>선언 순서 = {@code bodyIndex}이며 {@code typeCode}를 결정하므로 <b>순서 변경 금지</b>입니다.
 */
public enum BodyType implements KeyedEnum {
    STANDARD("standard"),
    SMALL_INV_TRIANGLE("small_inv_triangle"),
    LARGE_RECTANGLE("large_rectangle"),
    RECTANGLE("rectangle");

    private final String key;

    BodyType(String key) {
        this.key = key;
    }

    @Override
    @JsonValue
    public String key() {
        return key;
    }

    /** 유형 산출용 bodyIndex (standard=0 … rectangle=3). */
    public int index() {
        return ordinal();
    }

    public static Optional<BodyType> findByKey(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst();
    }

    @JsonCreator
    public static BodyType fromKey(String key) {
        return findByKey(key).orElseThrow(() -> new IllegalArgumentException("알 수 없는 체형 Key: " + key));
    }
}
