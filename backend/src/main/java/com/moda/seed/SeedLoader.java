package com.moda.seed;

import com.moda.global.config.ModaProperties;
import com.moda.global.config.ModaProperties.SeedMode;
import com.moda.seed.dto.SeedBundle;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 서버 기동 시 시드 JSON 5종을 읽어 <b>검증 → 코드 기준 upsert</b>합니다 (style-rules.md 5.3).
 * <ul>
 *   <li>{@code moda.seed.mode=upsert}(기본): 검증 실패 시 예외를 던져 <b>서버 기동을 중단</b>합니다.</li>
 *   <li>{@code moda.seed.mode=none}: 적재를 건너뜁니다 (이미 적재된 운영 DB 등).</li>
 * </ul>
 * 모든 싱글톤 생성 직후(웹 서버가 요청을 받기 전) 실행되므로 첫 API 요청 시점에는 시드가 항상 적재되어 있습니다.
 */
@Component
public class SeedLoader implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final ModaProperties properties;
    private final SeedReader reader;
    private final SeedValidator validator;
    private final SeedUpsertService upsertService;
    private final ApplicationEventPublisher eventPublisher;

    public SeedLoader(ModaProperties properties, SeedReader reader, SeedValidator validator, SeedUpsertService upsertService,
                      ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.reader = reader;
        this.validator = validator;
        this.upsertService = upsertService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterSingletonsInstantiated() {
        load();
    }

    /**
     * @return 적재 결과. {@code moda.seed.mode=none}이면 빈 값
     * @throws SeedValidationException 파일 누락·JSON 오류·검증 실패
     */
    public Optional<SeedLoadResult> load() {
        if (properties.seed().mode() == SeedMode.NONE) {
            log.info("[Seed] moda.seed.mode=none — 시드 적재를 건너뜁니다.");
            return Optional.empty();
        }
        try {
            SeedBundle bundle = reader.read();
            validator.validate(bundle);
            SeedLoadResult result = upsertService.upsert(bundle);
            log.info("[Seed] 시드 적재 완료 (ruleVersion={}, inserted={}, updated={}) {}",
                    result.ruleVersion(), result.totalInserted(), result.totalUpdated(), result.summary());
            eventPublisher.publishEvent(new SeedLoadedEvent(result));
            return Optional.of(result);
        } catch (SeedValidationException e) {
            log.error("[Seed] {}", e.getMessage());
            throw e;
        }
    }
}
