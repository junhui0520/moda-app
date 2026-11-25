package com.moda.common.enums;

/** 분석 상세 상태 (docs/api-spec.md 2장, ⑥ status 판정 규칙). */
public enum AnalysisStatus {
    /** AI 결과 사용 중 (source=AI) */
    ANALYZED,
    /** AI 결과를 사용자가 수정 (source=MANUAL, aiResult != null) */
    CORRECTED,
    /** AI 결과 없이 사용자가 직접 선택 (source=MANUAL, aiResult == null) */
    MANUAL,
    /** 미분석 */
    NOT_ANALYZED
}
