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

/** Profilde listelenen bağlantı (V29) — "link in bio" satırı. */
@Entity
@Table(name = "profile_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileLink extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title", nullable = false, length = 120)
    @Setter
    private String title;

    /** 🔒 Yalnız http/https — `normalizeWebsite` ile doğrulanır. */
    @Column(name = "url", nullable = false, length = 500)
    @Setter
    private String url;

    @Column(name = "icon_media_id")
    @Setter
    private UUID iconMediaId;

    @Column(name = "position", nullable = false)
    @Setter
    private int position;

    @Column(name = "click_count", nullable = false)
    private int clickCount;

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active = true;

    public ProfileLink(UUID ownerId, String title, String url) {
        this.ownerId = ownerId;
        this.title = title;
        this.url = url;
    }
}
