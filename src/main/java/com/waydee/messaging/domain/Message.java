package com.waydee.messaging.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * DM mesajı. clientMessageId istemcinin ürettiği idempotency/eşleme anahtarıdır:
 * optimistic gönderim WS yankısıyla bu id üzerinden birleştirilir, çift gönderim önlenir.
 */
@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "body", nullable = false, length = 2000)
    private String body;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    public Message(UUID conversationId, UUID senderId, String body, String clientMessageId) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
        this.clientMessageId = clientMessageId;
    }
}
