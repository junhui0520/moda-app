package com.moda.session.service;

import java.time.Instant;

/**
 * 세션에 딸린 하위 데이터(예: 챗봇 대화 내역 {@code chat_message})를 세션과 함께 파기하는 확장점.
 * <p>세션 모듈이 하위 모듈에 의존하지 않도록 하위 모듈이 구현합니다. ⑧ 세션 초기화와 만료 세션 정리 스케줄러가
 * 세션 행을 지우기 직전에 같은 트랜잭션 안에서 호출합니다. (DB FK {@code ON DELETE CASCADE}와 함께 이중으로 보장)
 */
public interface SessionDataPurger {

    /** ⑧ 세션 초기화: 해당 세션의 하위 데이터를 삭제하고 삭제 건수를 반환합니다. */
    int purgeSession(String sessionId);

    /** 만료 정리: {@code expiresAt <= now} 인 세션들의 하위 데이터를 삭제하고 삭제 건수를 반환합니다. */
    int purgeExpired(Instant now);
}
