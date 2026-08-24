package com.waydee.social.domain;

import com.waydee.common.storage.MediaUrls;
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

@Entity
@Table(name = "media_objects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaObject {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "object_key", nullable = false, length = 120)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MediaObject(UUID ownerId, String objectKey, String contentType, long sizeBytes) {
        this.ownerId = ownerId;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
    }

    public String publicUrl() {
        return MediaUrls.of(id);
    }
}
