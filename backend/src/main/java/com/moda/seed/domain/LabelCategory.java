package com.moda.seed.domain;

/** {@code analysis_label} 분류 (docs/api-spec.md 3.9, 부록 A.4, A.5). */
public enum LabelCategory {
    /** AI features 키 라벨 */
    FEATURE,
    /** Size Korea 기준 데이터 (예: SIZE_KOREA_20S_MALE) */
    SIZE_KOREA_REFERENCE,
    /** Size Korea 항목 라벨 */
    SIZE_KOREA_ITEM,
    /** AI 경고 코드 (severity + message) */
    WARNING,
    /** 정의되지 않은 경고 코드 기본값 */
    WARNING_FALLBACK
}
