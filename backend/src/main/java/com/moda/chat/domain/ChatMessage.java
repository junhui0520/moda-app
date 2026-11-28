package com.moda.chat.domain;

import com.moda.common.enums.ChatRole;
import com.moda.common.jpa.StringListConverter;
import com.moda.session.domain.GuestSession;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 챗봇 대화 메시지 ({@code chat_message}, docs/api-spec.md ⑨·⑩).
 * <ul>
 *   <li>세션 FK {@code ON DELETE CASCADE}: 세션 삭제(⑧)·만료 정리 시 대화 내역이 DB 수준에서 함께 삭제됩니다 (1.4).</li>
 *   <li>{@code sequenceNo}: 세션 내 순번. 1턴 = USER({@code 2n-1}) + ASSISTANT({@code 2n}).
 *       {@code (session_id, sequence_no)} 유니크 제약이 같은 턴의 중복 저장을 DB 수준에서 막습니다.</li>
 *   <li>{@code welcomeMessage}는 저장하지 않습니다.</li>
 * </ul>
 */
@Entity
@Table(name = "chat_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_message_session_seq", columnNames = {"session_id", "sequence_no"}),
        indexes = @Index(name = "idx_chat_message_session", columnList = "session_id, sequence_no"))
public class ChatMessage {

    public static final int MAX_CONTENT_LENGTH = 4000;

    @Id
    @Column(name = "message_id", length = 36)
    private String messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_message_session"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private GuestSession session;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ChatRole role;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 답변과 함께 보여 줄 스타일 이미지 키 (catalog imageKey, ASSISTANT만). */
    @Convert(converter = StringListConverter.class)
    @Column(name = "image_keys", length = 500)
    private List<String> imageKeys;

    protected ChatMessage() {
    }

    public ChatMessage(String messageId, GuestSession session, int sequenceNo, ChatRole role, String content, Instant createdAt) {
        this(messageId, session, sequenceNo, role, content, createdAt, List.of());
    }

    public ChatMessage(String messageId, GuestSession session, int sequenceNo, ChatRole role, String content, Instant createdAt,
                       List<String> imageKeys) {
        this.messageId = messageId;
        this.session = session;
        this.sequenceNo = sequenceNo;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
        this.imageKeys = imageKeys;
    }

    public List<String> getImageKeys() {
        return imageKeys == null ? List.of() : imageKeys;
    }

    public String getMessageId() {
        return messageId;
    }

    public GuestSession getSession() {
        return session;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public ChatRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
