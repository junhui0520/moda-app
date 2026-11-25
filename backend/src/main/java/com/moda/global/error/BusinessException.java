package com.moda.global.error;

import com.moda.global.error.ApiError.FieldErrorDetail;
import java.util.List;
import java.util.Map;

/**
 * 비즈니스 규칙 위반. {@link GlobalExceptionHandler}가 {@link ErrorCode}의 HTTP 상태와 공통 에러 포맷으로 변환합니다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldErrorDetail> details;
    private final Map<String, Object> meta;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of(), null, null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, List.of(), null, cause);
    }

    public BusinessException(ErrorCode errorCode, String message, List<FieldErrorDetail> details,
                             Map<String, Object> meta, Throwable cause) {
        super(message == null ? errorCode.defaultMessage() : message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
        this.meta = meta;
    }

    public static BusinessException withMeta(ErrorCode errorCode, Map<String, Object> meta) {
        return new BusinessException(errorCode, errorCode.defaultMessage(), List.of(), meta, null);
    }

    public static BusinessException invalidField(String field, String reason, Object rejectedValue) {
        return new BusinessException(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.defaultMessage(),
                List.of(new FieldErrorDetail(field, reason, rejectedValue)), null, null);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<FieldErrorDetail> getDetails() {
        return details;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
