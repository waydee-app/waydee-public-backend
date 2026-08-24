package com.waydee.moderation.domain;

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

/** Bir kullanıcının belirli bir eylemi yapmasını engelleyen kayıt (süreli olabilir). */
@Entity
@Table(name = "user_restrictions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRestriction {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private RestrictedAction action;

    @Column(name = "reason", length = 300)
    @Setter
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** null = süresiz. */
    @Column(name = "expires_at")
    @Setter
    private Instant expiresAt;

    public UserRestriction(UUID userId, RestrictedAction action, String reason, UUID createdBy, Instant expiresAt) {
        this.userId = userId;
        this.action = action;
        this.reason = reason;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
