package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * "Son hareketler" akışı satırı (append-only). Görüntüleme alanları yazım
 * anında denormalize edilir → okuma tamamen joinsizdir. İmzalı URL saklanmaz
 * (7 günde bayatlar); avatar id saklanır, okurken taze imzalanır.
 */
@Entity
@Table(name = "activity_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityEvent {

    @Id
    @UuidGenerator
    private UUID id;

    /** TERRITORY_PURCHASED | PHOTO_SHARED | POST_SHARED | POLL_CREATED | EVENT_STARTED */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_username", nullable = false, length = 30)
    private String actorUsername;

    @Column(name = "actor_display_name", nullable = false, length = 60)
    private String actorDisplayName;

    @Column(name = "actor_avatar_media_id")
    private UUID actorAvatarMediaId;

    @Column(name = "territory_id")
    private UUID territoryId;

    @Column(name = "territory_name", length = 140)
    private String territoryName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ActivityEvent(String type, UUID actorId, String actorUsername, String actorDisplayName,
                         UUID actorAvatarMediaId, UUID territoryId, String territoryName) {
        this.type = type;
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.actorDisplayName = actorDisplayName;
        this.actorAvatarMediaId = actorAvatarMediaId;
        this.territoryId = territoryId;
        this.territoryName = territoryName;
        this.createdAt = Instant.now();
    }
}
