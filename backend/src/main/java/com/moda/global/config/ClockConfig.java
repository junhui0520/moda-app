package com.moda.global.config;

import com.moda.global.time.KstTime;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 의존 로직(세션 TTL·만료 정리)을 테스트에서 고정할 수 있도록 {@link Clock}을 빈으로 제공합니다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(KstTime.ZONE);
    }
}
