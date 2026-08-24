package com.waydee.social.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Satın alınan her alanın sosyal profili. Alan satın alındığı anda
 * (TerritoryPurchasedEvent ile) boş profil olarak oluşturulur.
 */
@Entity
@Table(name = "territory_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryProfile extends AuditableEntity {

    @Id
    @Column(name = "territory_id")
    private UUID territoryId;

    @Column(name = "title", length = 80)
    @Setter
    private String title;

    @Column(name = "description", length = 500)
    @Setter
    private String description;

    @Column(name = "website", length = 200)
    @Setter
    private String website;

    @Column(name = "post_count", nullable = false)
    private int postCount;

    /** Gösterim türü: STANDARD (akış) · WEBSITE (gömülü site) · HTML (kullanıcı mini sitesi). */
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", nullable = false, length = 20)
    @Setter
    private ProfileType profileType = ProfileType.STANDARD;

    /** Kullanıcının yazdığı HTML — **sunucuda temizlenmiş** hali saklanır. */
    @Column(name = "custom_html", columnDefinition = "text")
    @Setter
    private String customHtml;

    /** Sahibinin sosyal medya bağlantıları bu bölge profilinde gösterilsin mi. */
    /** Kartın üstünde gösterilen öne çıkan görsel (opsiyonel). */
    @Column(name = "featured_media_id")
    @Setter
    private UUID featuredMediaId;

    /** Canlı yayın bağlantısı — doluysa kartta "Canlı Yayın" rozeti çıkar. */
    @Column(name = "live_url", length = 300)
    @Setter
    private String liveUrl;

    /** Yayın şu an açık mı (sahibi elle açıp kapatır). */
    @Column(name = "live_active", nullable = false)
    @Setter
    private boolean liveActive = false;

    @Column(name = "show_social_links", nullable = false)
    @Setter
    private boolean showSocialLinks = true;

    public TerritoryProfile(UUID territoryId, String title) {
        this.territoryId = territoryId;
        this.title = title;
        this.postCount = 0;
    }
}
