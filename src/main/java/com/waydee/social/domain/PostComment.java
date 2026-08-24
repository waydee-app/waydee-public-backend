package com.waydee.social.domain;

import com.waydee.common.persistence.AuditableEntity;
import com.waydee.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public PostComment(UUID postId, User author, String body) {
        this.postId = postId;
        this.author = author;
        this.body = body;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
