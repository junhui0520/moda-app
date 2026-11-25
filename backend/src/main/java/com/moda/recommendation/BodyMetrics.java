package com.moda.recommendation;

import com.moda.common.enums.AgeBand;
import com.moda.common.enums.BmiBand;
import com.moda.common.enums.HeightBand;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 파생 구간 계산 (docs/style-rules.md 1.7). 백엔드 내부 전용이며 BMI 수치·구간명은 어떤 응답에도 넣지 않습니다 (R-11).
 */
public final class BodyMetrics {

    private static final BigDecimal BMI_LOW_MAX = new BigDecimal("18.5");
    private static final BigDecimal BMI_HIGH_MIN = new BigDecimal("23.0");
    private static final BigDecimal BMI_VERY_HIGH_MIN = new BigDecimal("25.0");

    private BodyMetrics() {
    }

    /** {@code weightKg ÷ (heightCm/100)²}, 소수 첫째 자리 반올림 (HALF_UP). */
    public static BigDecimal bmi(int heightCm, int weightKg) {
        BigDecimal heightM = BigDecimal.valueOf(heightCm).movePointLeft(2);
        return BigDecimal.valueOf(weightKg)
                .divide(heightM.multiply(heightM), MathContext.DECIMAL64)
                .setScale(1, RoundingMode.HALF_UP);
    }

    /** {@code weightKg}가 없으면 구간 없음(BMI 보정 생략). */
    public static Optional<BmiBand> bmiBand(int heightCm, Integer weightKg) {
        if (weightKg == null) {
            return Optional.empty();
        }
        BigDecimal bmi = bmi(heightCm, weightKg);
        if (bmi.compareTo(BMI_LOW_MAX) < 0) {
            return Optional.of(BmiBand.BMI_LOW);
        }
        if (bmi.compareTo(BMI_HIGH_MIN) < 0) {
            return Optional.of(BmiBand.BMI_MID);
        }
        if (bmi.compareTo(BMI_VERY_HIGH_MIN) < 0) {
            return Optional.of(BmiBand.BMI_HIGH);
        }
        return Optional.of(BmiBand.BMI_VERY_HIGH);
    }

    public static HeightBand heightBand(int heightCm) {
        if (heightCm < 170) {
            return HeightBand.HEIGHT_SHORT;
        }
        return heightCm >= 182 ? HeightBand.HEIGHT_TALL : HeightBand.HEIGHT_MID;
    }

    public static AgeBand ageBand(int age) {
        if (age <= 22) {
            return AgeBand.AGE_U22;
        }
        if (age <= 29) {
            return AgeBand.AGE_23_29;
        }
        return age <= 39 ? AgeBand.AGE_30S : AgeBand.AGE_40P;
    }
}
