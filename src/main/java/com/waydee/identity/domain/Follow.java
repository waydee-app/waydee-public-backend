package com.waydee.identity.domain;

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

/** Bir kullanıcının (follower) başka bir kullanıcıyı (followee) takibi. */
@Entity
@Table(name = "follows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "follower_id", nullable = false)
    private UUID followerId;

    @Column(name = "followee_id", nullable = false)
    private UUID followeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    @Setter
    private FollowStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Follow(UUID followerId, UUID followeeId, FollowStatus status) {
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.status = status;
        this.createdAt = Instant.now();
    }
}
