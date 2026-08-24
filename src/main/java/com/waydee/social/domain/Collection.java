package com.waydee.social.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** Kullanıcının gönderilerini gruplayan koleksiyon (V29). */
@Entity
@Table(name = "collections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collection extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 120)
    @Setter
    private String title;

    @Column(name = "description", length = 500)
    @Setter
    private String description;

    @Column(name = "cover_media_id")
    @Setter
    private UUID coverMediaId;

    @Column(name = "position", nullable = false)
    @Setter
    private int position;

    /** Denormalize: ızgara her kart için ayrı COUNT çalıştırmasın (N+1). */
    @Column(name = "item_count", nullable = false)
    @Setter
    private int itemCount;

    public Collection(UUID ownerId, String title) {
        this.ownerId = ownerId;
        this.title = title;
    }
}
