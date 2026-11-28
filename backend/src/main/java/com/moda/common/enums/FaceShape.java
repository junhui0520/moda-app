package com.moda.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

/**
 * 얼굴형 6종 (docs/style-rules.md 1.1).
 * <p>선언 순서 = {@code faceIndex}이며 {@code typeCode}를 결정하므로 <b>순서 변경 금지</b>입니다.
 */
public enum FaceShape implements KeyedEnum {
    DIAMOND("diamond"),
    HEART("heart"),
    OVAL("oval"),
    RECTANGLE("rectangle"),
    ROUND("round"),
    LONG("long");

    private final String key;

    FaceShape(String key) {
        this.key = key;
    }

    @Override
    @JsonValue
    public String key() {
        return key;
    }

    /** 유형 산출용 faceIndex (diamond=0 … long=5). */
    public int index() {
        return ordinal();
    }

    public static Optional<FaceShape> findByKey(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst();
    }

    @JsonCreator
    public static FaceShape fromKey(String key) {
        return findByKey(key).orElseThrow(() -> new IllegalArgumentException("알 수 없는 얼굴형 Key: " + key));
    }
}
