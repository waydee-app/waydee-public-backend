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

/** Bir hikayeyi kimin gördüğü — "görüldü" durumu ve görüntülenme sayısı için. */
@Entity
@Table(name = "story_views")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryView {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    public StoryView(UUID storyId, UUID viewerId) {
        this.storyId = storyId;
        this.viewerId = viewerId;
        this.viewedAt = Instant.now();
    }
}
