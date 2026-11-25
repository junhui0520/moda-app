package com.moda.global.error;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 공통 에러 포맷 (docs/api-spec.md 4장). {@code details}는 없으면 {@code []}, {@code meta}는 없으면 {@code null}.
 */
public record ApiError(
        int status,
        ErrorCode code,
        String message,
        List<FieldErrorDetail> details,
        Map<String, Object> meta,
        String path,
        OffsetDateTime timestamp) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    /** {@code {"field": string, "reason": string, "rejectedValue": any | null}} */
    public record FieldErrorDetail(String field, String reason, Object rejectedValue) {
    }
}
