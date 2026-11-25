package com.moda.global.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.moda.global.error.ApiError.FieldErrorDetail;
import com.moda.global.response.ApiResponse;
import com.moda.global.time.KstTime;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 모든 예외를 공통 에러 포맷(docs/api-spec.md 4장)으로 변환합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        if (e.getErrorCode().status().is5xxServerError()) {
            log.warn("[{}] {} {} — {}", e.getErrorCode(), request.getMethod(), request.getRequestURI(), e.getMessage(), e.getCause());
        }
        return build(e.getErrorCode(), e.getErrorCode().status(), e.getMessage(), e.getDetails(), e.getMeta(), request);
    }

    /** {@code @Valid} 요청 본문 검증 실패 → {@code 400 INVALID_REQUEST} + {@code details[]} (요청 DTO 필드 선언 순서). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        BindingResult binding = e.getBindingResult();
        List<String> order = binding.getTarget() == null ? List.of()
                : Arrays.stream(binding.getTarget().getClass().getDeclaredFields()).map(Field::getName).toList();
        List<FieldErrorDetail> details = binding.getFieldErrors().stream()
                .sorted(Comparator.comparingInt((FieldError fe) -> indexOf(order, fe.getField())).thenComparing(FieldError::getField))
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();
        return build(ErrorCode.INVALID_REQUEST, null, null, details, null, request);
    }

    /** JSON 문법 오류·타입 불일치(예: {@code "age": "abc"}) → {@code 400 INVALID_REQUEST}. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        List<FieldErrorDetail> details = new ArrayList<>();
        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            Object rejected = mismatch instanceof InvalidFormatException invalid ? invalid.getValue() : null;
            details.add(new FieldErrorDetail(fieldPath(mismatch), typeReason(mismatch.getTargetType()), rejected));
        }
        return build(ErrorCode.INVALID_REQUEST, null, null, details, null, request);
    }

    /** 업로드 크기 초과(파일 10MB / 요청 12MB) → {@code 413 IMAGE_TOO_LARGE}. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(ErrorCode.IMAGE_TOO_LARGE, null, null, List.of(), null, request);
    }

    /** {@code image} 파트 누락·멀티파트 형식 오류 → {@code 400 IMAGE_REQUIRED}. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMultipart(Exception e, HttpServletRequest request) {
        return build(ErrorCode.IMAGE_REQUIRED, null, null, List.of(), null, request);
    }

    /**
     * 그 외: Spring MVC 표준 예외(지원하지 않는 메서드 405, 없는 경로 404 등)는 원래 HTTP 상태 + {@code INVALID_REQUEST},
     * 예상하지 못한 예외는 {@code 500 INTERNAL_ERROR}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOthers(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return build(ErrorCode.INVALID_REQUEST, errorResponse.getStatusCode(), null, List.of(), null, request);
        }
        log.error("[INTERNAL_ERROR] {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(ErrorCode.INTERNAL_ERROR, null, null, List.of(), null, request);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, HttpStatusCode status, String message,
                                                    List<FieldErrorDetail> details, Map<String, Object> meta,
                                                    HttpServletRequest request) {
        HttpStatusCode httpStatus = status == null ? code.status() : status;
        ApiError error = new ApiError(
                httpStatus.value(),
                code,
                message == null ? code.defaultMessage() : message,
                details,
                meta,
                request.getRequestURI(),
                KstTime.toOffset(KstTime.now(clock)));
        return ResponseEntity.status(httpStatus).body(ApiResponse.failure(error));
    }

    private static int indexOf(List<String> order, String field) {
        String root = field.contains(".") ? field.substring(0, field.indexOf('.')) : field;
        int i = order.indexOf(root);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static String fieldPath(JsonMappingException e) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    private static String typeReason(Class<?> type) {
        if (type == null) {
            return "형식이 올바르지 않습니다.";
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "정수로 입력해 주세요.";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true 또는 false로 입력해 주세요.";
        }
        if (type == String.class) {
            return "문자열로 입력해 주세요.";
        }
        return "형식이 올바르지 않습니다.";
    }
}
