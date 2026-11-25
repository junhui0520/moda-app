package com.moda.chat.service;

import com.moda.chat.repository.ChatMessageRepository;
import com.moda.session.service.SessionDataPurger;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** ⑧ 세션 초기화·만료 정리 시 챗봇 대화 내역({@code chat_message})을 즉시 삭제합니다 (api-spec.md 1.4). */
@Component
public class ChatMessagePurger implements SessionDataPurger {

    private final ChatMessageRepository repository;

    public ChatMessagePurger(ChatMessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public int purgeSession(String sessionId) {
        return repository.deleteBySession(sessionId);
    }

    @Override
    public int purgeExpired(Instant now) {
        return repository.deleteForExpiredSessions(now);
    }
}
