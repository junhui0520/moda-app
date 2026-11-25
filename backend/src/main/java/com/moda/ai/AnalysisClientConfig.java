package com.moda.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moda.global.config.ModaProperties;
import com.moda.global.config.ModaProperties.ClientMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code moda.ai.mode}({@code mock} | {@code external})에 따라 {@link AnalysisClient} 구현체를 선택합니다. */
@Configuration
public class AnalysisClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AnalysisClientConfig.class);

    @Bean
    public AnalysisClient analysisClient(ModaProperties properties, ObjectMapper objectMapper, AiResponseParser parser) {
        ModaProperties.Ai ai = properties.ai();
        if (ai.mode() == ClientMode.EXTERNAL) {
            log.info("[AI] moda.ai.mode=external — AI 서버 {} 호출", ai.baseUrl());
            return new ExternalAiAnalysisClient(ai, parser);
        }
        log.info("[AI] moda.ai.mode=mock — Mock 분석 결과 사용 (기본 지연 {}ms)", ai.mockDefaultDelay().toMillis());
        return new MockAnalysisClient(objectMapper, parser, ai.mockDefaultDelay());
    }
}
