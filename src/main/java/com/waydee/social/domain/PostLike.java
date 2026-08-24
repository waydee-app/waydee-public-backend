package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLike {

    @EmbeddedId
    private PostLikeId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PostLike(UUID postId, UUID userId) {
        this.id = new PostLikeId(postId, userId);
        this.createdAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class PostLikeId implements Serializable {

        @Column(name = "post_id")
        private UUID postId;

        @Column(name = "user_id")
        private UUID userId;

        public PostLikeId(UUID postId, UUID userId) {
            this.postId = postId;
            this.userId = userId;
        }
    }
}
