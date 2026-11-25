package com.moda.global.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드표 17종 (docs/api-spec.md 4.1). 프론트엔드는 {@code code}로 분기합니다.
 * 챗봇용 {@code CHAT_*} 3종은 Step 2-3(API ⑨·⑩)에서 사용합니다.
 */
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    PHOTO_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "사진 분석 동의가 필요해요."),
    IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "사진을 첨부해 주세요."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "체험 세션을 찾을 수 없어요. 처음부터 다시 시작해 주세요."),
    PROFILE_INPUT_REQUIRED(HttpStatus.CONFLICT, "기본 정보를 먼저 입력해 주세요."),
    ANALYSIS_INCOMPLETE(HttpStatus.CONFLICT, "얼굴과 전신 분석이 모두 필요해요."),
    ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "분석이 진행 중이에요. 잠시만 기다려 주세요."),
    SESSION_EXPIRED(HttpStatus.GONE, "체험 시간이 지나 세션이 종료되었어요. 처음부터 다시 시작해 주세요."),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "사진 용량은 10MB 이하만 가능해요."),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPG 또는 PNG 사진만 분석할 수 있어요."),
    CHAT_TURN_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "이번 체험에서 대화할 수 있는 횟수(20회)를 모두 사용했어요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),
    AI_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "분석 결과를 처리하지 못했어요. 다시 촬영해 주세요."),
    AI_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "분석 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요."),
    CHAT_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챗봇 서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "분석 시간이 초과되었어요. 다시 시도해 주세요."),
    CHAT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "답변 생성 시간이 초과되었어요. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
