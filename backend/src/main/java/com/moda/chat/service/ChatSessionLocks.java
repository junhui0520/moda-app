package com.moda.chat.service;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/**
 * 세션 단위 순차 처리용 잠금 (api-spec.md 챗봇 공통 규칙 "동시 전송").
 * 같은 세션의 ⑩ 요청은 도착 순서대로(공정 잠금) 하나씩 처리되어 턴 한도 검사·순번 부여가 원자적으로 이루어집니다.
 * 세션 수만큼 잠금 객체가 쌓이지 않도록 고정 개수의 스트라이프를 사용합니다 (MVP 단일 서버 기준).
 * 여러 서버로 확장하더라도 {@code chat_message (session_id, sequence_no)} 유니크 제약이 중복 저장을 막습니다.
 */
@Component
public class ChatSessionLocks {

    static final int STRIPES = 256;

    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    public ChatSessionLocks() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock(true);
        }
    }

    public ReentrantLock forSession(String sessionId) {
        return locks[Math.floorMod(sessionId.hashCode(), STRIPES)];
    }
}
