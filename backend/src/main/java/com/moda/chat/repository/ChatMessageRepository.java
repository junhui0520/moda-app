package com.moda.chat.repository;

import com.moda.chat.domain.ChatMessage;
import com.moda.common.enums.ChatRole;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    @Query("select m from ChatMessage m where m.session.sessionId = :sessionId order by m.sequenceNo asc")
    List<ChatMessage> findHistory(@Param("sessionId") String sessionId);

    @Query("select count(m) from ChatMessage m where m.session.sessionId = :sessionId and m.role = :role")
    long countByRole(@Param("sessionId") String sessionId, @Param("role") ChatRole role);

    @Query("select count(m) from ChatMessage m where m.session.sessionId = :sessionId")
    long countBySession(@Param("sessionId") String sessionId);

    @Modifying
    @Query("delete from ChatMessage m where m.session.sessionId = :sessionId")
    int deleteBySession(@Param("sessionId") String sessionId);

    @Modifying
    @Query("delete from ChatMessage m where m.session.sessionId in "
            + "(select s.sessionId from GuestSession s where s.expiresAt <= :now)")
    int deleteForExpiredSessions(@Param("now") Instant now);
}
