package com.waydee.social.domain;

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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Instagram tarzı hikaye. Kullanıcıya (author) aittir; {@code expiresAt} sonrası
 * gösterilmez (24 saat). Kalıcı silme gerekmez, sorgular aktifleri filtreler.
 */
@Entity
@Table(name = "stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "caption", length = 200)
    private String caption;

    /**
     * Hikayenin yayınlandığı bölge (harita profili). NULL = yalnız kullanıcı
     * profilinde görünen klasik hikaye. Yalnız profil türü STANDARD (akış) olan
     * bölgeler seçilebilir — gömülü site/HTML profilinde akış gösterilmez.
     */
    @Column(name = "territory_id")
    private UUID territoryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public Story(User author, UUID mediaId, String caption, UUID territoryId, Duration ttl) {
        this.author = author;
        this.mediaId = mediaId;
        this.caption = caption;
        this.territoryId = territoryId;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(ttl);
    }
}
