package com.moda.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.moda.global.error.ApiError;

/**
 * 공통 응답 Envelope (docs/api-spec.md 1.3).
 * <ul>
 *   <li>성공: {@code {"success": true, "data": {...}}} — {@code error} 키 없음</li>
 *   <li>실패: {@code {"success": false, "error": {...}}} — {@code data} 키 없음</li>
 * </ul>
 * {@link JsonInclude.Include#NON_NULL}은 이 클래스의 세 필드에만 적용됩니다. {@code data}·{@code error} 내부 객체의
 * {@code null} 필드는 전역 설정({@code default-property-inclusion: always})에 따라 생략되지 않고 {@code null}로 직렬화됩니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "data", "error"})
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
