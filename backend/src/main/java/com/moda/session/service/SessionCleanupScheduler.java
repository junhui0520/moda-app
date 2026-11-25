package com.moda.session.service;

import com.moda.global.config.ModaProperties;
import com.moda.global.time.KstTime;
import com.moda.session.repository.GuestSessionRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 만료 세션 정리 스케줄러 (docs/api-spec.md 1.4). 주기: {@code moda.session.cleanup-interval} (기본 5분).
 * 만료 세션과 관련 데이터(입력값·분석 결과, Step 2-3 이후 챗봇 대화 내역)를 삭제합니다.
 */
@Component
@EnableScheduling
public class SessionCleanupScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final GuestSessionRepository repository;
    private final Clock clock;
    private final Duration interval;
    private final TransactionTemplate tx;

    public SessionCleanupScheduler(GuestSessionRepository repository, Clock clock, ModaProperties properties,
                                   PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clock = clock;
        this.interval = properties.session().cleanupInterval();
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::cleanupExpiredSessions, interval, interval));
    }

    /** @return 삭제한 세션 수 */
    public int cleanupExpiredSessions() {
        Integer deleted = tx.execute(status -> repository.deleteExpired(KstTime.now(clock)));
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            log.info("[Session] 만료 세션 {}건 삭제", count);
        }
        return count;
    }
}
