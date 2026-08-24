package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Bir kullanıcıya (userId = alıcı) düşen bildirim. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private NotificationType type;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "territory_id")
    private UUID territoryId;

    /** POST_LIKE / POST_SAVE bildiriminin gonderisi (V39); digerlerinde bos. */
    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "is_read", nullable = false)
    @Setter
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Notification(UUID userId, NotificationType type, UUID actorId, UUID territoryId) {
        this(userId, type, actorId, territoryId, null);
    }

    public Notification(UUID userId, NotificationType type, UUID actorId, UUID territoryId, UUID postId) {
        this.userId = userId;
        this.type = type;
        this.actorId = actorId;
        this.territoryId = territoryId;
        this.postId = postId;
        this.read = false;
        this.createdAt = Instant.now();
    }
}
