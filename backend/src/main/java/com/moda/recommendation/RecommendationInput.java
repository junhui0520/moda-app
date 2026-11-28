package com.moda.recommendation;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import java.util.Objects;

/** 추천 엔진 입력: 1차 기준(얼굴형·체형) + 보조 기준(모질 3항목, 나이·키·몸무게). */
public record RecommendationInput(
        FaceShape faceShape,
        BodyType bodyType,
        int age,
        int heightCm,
        Integer weightKg,
        HairThickness hairThickness,
        HairCurl hairCurl,
        HairDensity hairDensity) {

    public RecommendationInput {
        Objects.requireNonNull(faceShape, "faceShape");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(hairThickness, "hairThickness");
        Objects.requireNonNull(hairCurl, "hairCurl");
        Objects.requireNonNull(hairDensity, "hairDensity");
    }
}
