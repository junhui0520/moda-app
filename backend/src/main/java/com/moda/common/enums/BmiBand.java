package com.moda.common.enums;

/**
 * BMI 구간 (docs/style-rules.md 1.7). 백엔드 내부 계산 전용이며 <b>어떤 응답에도 노출하지 않습니다</b> (R-11).
 * <p>{@code weightKg ÷ (heightCm/100)²}, 소수 첫째 자리 반올림 기준.
 */
public enum BmiBand {
    /** &lt; 18.5 */
    BMI_LOW,
    /** 18.5 ~ 22.9 */
    BMI_MID,
    /** 23.0 ~ 24.9 */
    BMI_HIGH,
    /** ≥ 25.0 */
    BMI_VERY_HIGH
}
