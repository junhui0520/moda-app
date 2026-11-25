package com.moda.seed;

import java.util.List;

/**
 * 시드 JSON 읽기·검증 실패. 서버 기동 중 발생하면 기동을 중단합니다 (잘못된 문구로 시연되는 상황 방지, style-rules.md 5.3).
 */
public class SeedValidationException extends RuntimeException {

    private final List<String> errors;

    public SeedValidationException(List<String> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public SeedValidationException(String error, Throwable cause) {
        super(buildMessage(List.of(error)), cause);
        this.errors = List.of(error);
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String buildMessage(List<String> errors) {
        return "시드 데이터 검증 실패 (" + errors.size() + "건) — 서버 기동을 중단합니다.\n - " + String.join("\n - ", errors);
    }
}
