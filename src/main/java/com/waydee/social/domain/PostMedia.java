package com.waydee.social.domain;

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

import java.util.UUID;

@Entity
@Table(name = "post_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostMedia {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaObject media;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    PostMedia(Post post, MediaObject media, int sortOrder) {
        this.post = post;
        this.media = media;
        this.sortOrder = sortOrder;
    }
}
