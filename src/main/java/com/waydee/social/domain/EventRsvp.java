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

/** Bir kullanıcının etkinlik katılım kaydı (etkinlik başına tek — uq_event_rsvps_user). */
@Entity
@Table(name = "event_rsvps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRsvp {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    @Setter
    private RsvpStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public EventRsvp(UUID postId, UUID userId, RsvpStatus status) {
        this.postId = postId;
        this.userId = userId;
        this.status = status;
        this.createdAt = Instant.now();
    }
}
