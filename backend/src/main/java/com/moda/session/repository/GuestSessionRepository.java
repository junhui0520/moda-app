package com.moda.session.repository;

import com.moda.session.domain.GuestSession;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestSessionRepository extends JpaRepository<GuestSession, String> {

    /** 만료 세션 일괄 삭제 (5분 주기 스케줄러). Step 2-3의 {@code chat_message}는 FK {@code ON DELETE CASCADE}로 함께 삭제됩니다. */
    @Modifying
    @Query("delete from GuestSession s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
