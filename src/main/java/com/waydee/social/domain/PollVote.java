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

/** Bir kullanıcının bir anketteki oyu (anket başına tek oy — uq_poll_votes_user). */
@Entity
@Table(name = "poll_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollVote {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "option_id", nullable = false)
    private UUID optionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PollVote(UUID postId, UUID optionId, UUID userId) {
        this.postId = postId;
        this.optionId = optionId;
        this.userId = userId;
        this.createdAt = Instant.now();
    }
}
