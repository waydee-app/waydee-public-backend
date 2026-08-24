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

/**
 * <b>Bir profil bağlantısına tıklama</b> (V46).
 *
 * <p>🔴 Bu tablo, <b>hiç var olmayan</b> bir ölçümü kurar:
 * {@code profile_links.click_count} kolonu duruyor ve arayüzde gösteriliyordu
 * ama <b>hiçbir yerde artırılmıyordu</b> — yani kullanıcı her zaman sıfır olan
 * bir sayıya bakıyordu.
 *
 * <p>⚠️ {@link #country} bir <b>ISO kodudur</b>, ülke adı değil. Ad istemcide
 * {@code Intl.DisplayNames} ile üretilir; sunucudan ad göndermek arayüzü tek
 * dile mahkûm ederdi (vault, 83. tur).
 *
 * <p>⚠️ {@link #visitorKey} ham IP <b>değildir</b>: IP + tarayıcı imzasının
 * SHA-256'sı. Ölçüm için ayırt edici bir anahtar yeterli; kişisel veriyi
 * saklamanın bir faydası yok.
 */
@Entity
@Table(name = "profile_link_clicks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileLinkClick {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    /** Bağlantının sahibi — "tüm bağlantılarım" sorgusu JOIN'siz koşsun diye. */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Tıklayan kullanıcı; <b>ziyaretçi oturum açmadıysa {@code null}</b>. */
    @Column(name = "user_id")
    private UUID userId;

    /** ISO 3166-1 alpha-2; başlık yoksa {@code null}. */
    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "visitor_key", nullable = false, length = 64)
    private String visitorKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProfileLinkClick(UUID linkId, UUID ownerId, UUID userId, String country, String visitorKey) {
        this.linkId = linkId;
        this.ownerId = ownerId;
        this.userId = userId;
        this.country = country;
        this.visitorKey = visitorKey;
        this.createdAt = Instant.now();
    }
}
