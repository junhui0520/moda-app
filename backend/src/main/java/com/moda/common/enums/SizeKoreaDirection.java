package com.moda.common.enums;

/** Size Korea 평균 대비 방향 (docs/api-spec.md 2장, 3.9). */
public enum SizeKoreaDirection {
    /** 평균보다 큼 */
    ABOVE,
    /** 평균보다 작음 */
    BELOW,
    /** 평균과 비슷 (|deviation| &lt; 0.02) */
    SIMILAR
}
