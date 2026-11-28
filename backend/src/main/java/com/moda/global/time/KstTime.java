package com.moda.global.time;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * API 날짜·시간 표기 (docs/api-spec.md 1.2): ISO-8601 + KST 오프셋, 초 단위 (예: {@code 2026-11-19T21:30:00+09:00}).
 * DB에는 {@link Instant}(UTC 기준 시각)로 저장하고 응답 직전에 KST로 변환합니다.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    /** 초 단위로 절삭한 현재 시각. */
    public static Instant now(Clock clock) {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).atZone(ZONE).toOffsetDateTime();
    }
}
