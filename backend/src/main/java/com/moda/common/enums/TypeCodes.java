package com.moda.common.enums;

import java.util.Objects;

/**
 * 맞춤 유형 코드 산출 (docs/style-rules.md 1.2, 5.1).
 * <pre>typeCode = "TYPE-" + 2자리 0채움(bodyIndex × 6 + faceIndex + 1)   # TYPE-01 ~ TYPE-24</pre>
 */
public final class TypeCodes {

    public static final int TYPE_COUNT = BodyType.values().length * FaceShape.values().length;

    private TypeCodes() {
    }

    public static int indexOf(BodyType bodyType, FaceShape faceShape) {
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(faceShape, "faceShape");
        return bodyType.index() * FaceShape.values().length + faceShape.index() + 1;
    }

    public static String of(BodyType bodyType, FaceShape faceShape) {
        return String.format("TYPE-%02d", indexOf(bodyType, faceShape));
    }
}
