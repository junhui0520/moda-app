package com.moda.session.dto;

import com.moda.common.enums.BodyType;
import com.moda.common.enums.FaceShape;
import com.moda.common.enums.HairCurl;
import com.moda.common.enums.HairDensity;
import com.moda.common.enums.HairThickness;
import com.moda.global.validation.EnumValue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Range;

/** 요청 본문 (docs/api-spec.md ①②⑦). Enum 값은 문자열로 받아 검증하므로 여러 필드 오류가 {@code details[]}에 함께 담깁니다. */
public final class SessionRequests {

    static final String REQUIRED = "필수 입력 항목입니다.";

    private SessionRequests() {
    }

    /** ① {@code photoConsent}가 true가 아니면(false·누락) {@code 400 PHOTO_CONSENT_REQUIRED}. */
    public record GuestSessionRequest(
            Boolean photoConsent,
            @Size(max = 50, message = "50자 이하로 입력해 주세요.") String deviceLabel) {
    }

    /** ② 기본 정보 (전체 덮어쓰기). */
    public record ProfileInputRequest(
            @NotNull(message = REQUIRED)
            @Range(min = 14, max = 80, message = "14 이상 80 이하의 정수로 입력해 주세요.")
            Integer age,

            @NotNull(message = REQUIRED)
            @Range(min = 140, max = 210, message = "140 이상 210 이하의 정수로 입력해 주세요.")
            Integer heightCm,

            @Range(min = 30, max = 200, message = "30 이상 200 이하의 정수로 입력해 주세요.")
            Integer weightKg,

            @NotNull(message = REQUIRED) @EnumValue(enumClass = HairThickness.class)
            String hairThickness,

            @NotNull(message = REQUIRED) @EnumValue(enumClass = HairCurl.class)
            String hairCurl,

            @NotNull(message = REQUIRED) @EnumValue(enumClass = HairDensity.class)
            String hairDensity) {
    }

    /** ⑦ 보낸 필드만 변경 (키 생략 또는 null이면 변경하지 않음). 최소 1개 필드 필요. */
    public record ProfilePatchRequest(
            @EnumValue(enumClass = FaceShape.class) String faceShape,
            @EnumValue(enumClass = BodyType.class) String bodyType,
            @EnumValue(enumClass = HairThickness.class) String hairThickness,
            @EnumValue(enumClass = HairCurl.class) String hairCurl,
            @EnumValue(enumClass = HairDensity.class) String hairDensity) {

        public boolean noFieldPresent() {
            return faceShape == null && bodyType == null && hairThickness == null && hairCurl == null && hairDensity == null;
        }

        public boolean containsHairField() {
            return hairThickness != null || hairCurl != null || hairDensity != null;
        }
    }
}
