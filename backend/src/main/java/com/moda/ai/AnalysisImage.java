package com.moda.ai;

/**
 * 메모리에만 존재하는 업로드 사진 (R-01: 디스크에 저장하지 않음). 분석이 끝나면 호출 측이 {@code bytes}를 0으로 덮어써 폐기합니다.
 *
 * @param requestId AI 서버 로그 추적용 임의 ID (세션 ID와 무관)
 */
public record AnalysisImage(byte[] bytes, String contentType, String filename, String requestId) {

    public int size() {
        return bytes == null ? 0 : bytes.length;
    }
}
