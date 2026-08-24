package com.waydee.marketplace.domain;

import com.waydee.common.persistence.AuditableEntity;
import com.waydee.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir pazar yerindeki <b>stant</b> (üyenin projesi/ürünü).
 *
 * Yaşam döngüsü: DRAFT → PENDING → APPROVED | REJECTED (→ tekrar PENDING).
 * Yalnız APPROVED olanlar haritada ve vitrinde görünür.
 */
@Entity
@Table(name = "marketplace_listings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketplaceListing extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "marketplace_id", nullable = false)
    private UUID marketplaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // ---------------------------------------------------------------- içerik
    @Column(name = "title", nullable = false, length = 90)
    @Setter
    private String title;

    @Column(name = "tagline", length = 140)
    @Setter
    private String tagline;

    @Column(name = "description", nullable = false, length = 3000)
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    @Setter
    private ListingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 30)
    @Setter
    private ListingStage stage;

    @Column(name = "website", length = 300)
    @Setter
    private String website;

    @Column(name = "contact_email", length = 255)
    @Setter
    private String contactEmail;

    @Column(name = "logo_media_id")
    @Setter
    private UUID logoMediaId;

    @Column(name = "cover_media_id")
    @Setter
    private UUID coverMediaId;

    @Column(name = "founded_year")
    @Setter
    private Integer foundedYear;

    @Column(name = "team_size")
    @Setter
    private Integer teamSize;

    /** "Yatırımcı arıyoruz", "Ortak arıyoruz" gibi kısa bir çağrı. */
    @Column(name = "looking_for", length = 300)
    @Setter
    private String lookingFor;

    // ---------------------------------------------------------------- türe göre alanlar
    /** Etkinlik / gezi / yürüyüş: başlangıç ve bitiş. */
    @Column(name = "starts_at")
    @Setter
    private Instant startsAt;

    @Column(name = "ends_at")
    @Setter
    private Instant endsAt;

    /** Buluşma noktası / adres (serbest metin). */
    @Column(name = "location_label", length = 200)
    @Setter
    private String locationLabel;

    /** Kontenjan (etkinlik/gezi). */
    @Column(name = "capacity")
    @Setter
    private Integer capacity;

    /** İlan fiyatı. */
    @Column(name = "price", precision = 12, scale = 2)
    @Setter
    private java.math.BigDecimal price;

    @Column(name = "currency", length = 3)
    @Setter
    private String currency;

    /** NEW | LIKE_NEW | GOOD | USED | FOR_PARTS */
    @Column(name = "condition_code", length = 20)
    @Setter
    private String conditionCode;

    @Column(name = "contact_phone", length = 30)
    @Setter
    private String contactPhone;

    /** Adminin tanımladığı serbest soruların cevapları (JSON). */
    @Column(name = "custom_fields", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Setter
    private String customFields;

    /** Galeri görselleri (logo/kapak dışında, en fazla 8). */
    @Column(name = "gallery_media_ids", columnDefinition = "uuid[]")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    @Setter
    private UUID[] galleryMediaIds;

    // ---------------------------------------------------------------- yerleşim
    /** Onaylanınca pazar yerinin İÇİNDE atanan nokta. */
    @Column(name = "spot", columnDefinition = "geometry(Point,4326)")
    private Point spot;

    /** Kaçıncı stant — yerleşim deseni bu sırayla üretilir. */
    @Column(name = "spot_index")
    private Integer spotIndex;

    // ---------------------------------------------------------------- iş akışı
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status = ListingStatus.PENDING;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** Vitrinde öne çıkarılır (admin seçer). */
    @Column(name = "featured", nullable = false)
    @Setter
    private boolean featured = false;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    public MarketplaceListing(UUID marketplaceId, User owner, String title,
                              String description, ListingCategory category) {
        this.marketplaceId = marketplaceId;
        this.owner = owner;
        this.title = title;
        this.description = description;
        this.category = category;
        this.status = ListingStatus.PENDING;
        this.submittedAt = Instant.now();
    }

    // ---------------------------------------------------------------- geçişler

    /** Onayla ve stant noktasını yerleştir. */
    public void approve(UUID reviewerId, Point spot, int spotIndex, String note) {
        this.status = ListingStatus.APPROVED;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewerId;
        this.reviewNote = note;
        this.spot = spot;
        this.spotIndex = spotIndex;
    }

    /**
     * Reddet. Stant noktası TEMİZLENİR — aksi halde reddedilen bir başvuru
     * haritada yer tutmaya devam ederdi.
     */
    public void reject(UUID reviewerId, String note) {
        this.status = ListingStatus.REJECTED;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewerId;
        this.reviewNote = note;
        this.spot = null;
        this.spotIndex = null;
    }

    /** Reddedilen/geri çekilen başvuru düzenlenip yeniden gönderilir. */
    public void resubmit() {
        this.status = ListingStatus.PENDING;
        this.submittedAt = Instant.now();
        this.reviewedAt = null;
        this.reviewedBy = null;
        this.reviewNote = null;
    }

    public void withdraw() {
        this.status = ListingStatus.WITHDRAWN;
        this.spot = null;
        this.spotIndex = null;
    }

    public boolean isVisible() {
        return status == ListingStatus.APPROVED;
    }
}
