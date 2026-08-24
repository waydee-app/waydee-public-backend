package com.waydee.marketplace.domain;

import com.waydee.common.geo.GeoUtils;
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
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Adminin haritada çizdiği <b>pazar yeri</b>.
 *
 * Bölge (territory) DEĞİLDİR: sahiplik vermez, satın alınmaz, çakışma kuralı
 * doğurmaz. Yalnızca "burada bir pazar var" der; üyeler içine <b>stant</b>
 * ({@link MarketplaceListing}) açar.
 */
@Entity
@Table(name = "marketplaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Marketplace extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** URL'de kullanılan okunur kimlik (`/pazar/tekno-vadi`). */
    @Column(name = "slug", nullable = false, length = 60)
    @Setter
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    @Setter
    private String name;

    @Column(name = "tagline", length = 200)
    @Setter
    private String tagline;

    @Column(name = "description", length = 2000)
    @Setter
    private String description;

    @Column(name = "boundary", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    @Column(name = "center", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point center;

    @Column(name = "area_km2", nullable = false, precision = 14, scale = 6)
    private BigDecimal areaKm2;

    @Column(name = "accent_color", nullable = false, length = 9)
    @Setter
    private String accentColor = "#8e59ff";

    @Column(name = "cover_media_id")
    @Setter
    private UUID coverMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private MarketplaceStatus status = MarketplaceStatus.DRAFT;

    /** Pazarın türü — başvuru formunun varsayılanını belirler. */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    @Setter
    private MarketplaceKind kind = MarketplaceKind.GENERAL;

    /**
     * Adminin tasarladığı form şeması (JSON). null → türün varsayılanı.
     * Ham JSON tutulur; okuma/yazma {@code MarketplaceService} içinde
     * {@link FormSchema}'ya çevrilir — entity Jackson'a bağımlı kalmasın.
     */
    @Column(name = "form_schema", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Setter
    private String formSchema;

    /** Başvuru ekranında gösterilen kurallar/şartlar metni. */
    @Column(name = "application_note", length = 1000)
    @Setter
    private String applicationNote;

    /** 🔴 Doğrudan YAZILMAZ — {@link #schedule(Instant, Integer)} ile birlikte set edilir. */
    @Column(name = "opens_at")
    private Instant opensAt;

    /**
     * 🔴 Doğrudan YAZILMAZ — {@link #schedule(Instant, Integer)} türetir.
     *
     * <p>Setter bilinçli olarak yoktur: süre ile kapanış anının iki bağımsız
     * alan olarak elle doldurulması, vault'taki <i>"aynı gerçeğin iki alanı
     * taşınmada mutlaka ayrışır"</i> tuzağının birebir aynısıydı.
     */
    @Column(name = "closes_at")
    private Instant closesAt;

    /**
     * Pazarın kaç gün açık kalacağı (admin bunu girer).
     *
     * <p>{@code null} → süresiz. Kapanış anı bundan <b>türetilir</b>; sorgular
     * ve indeksler mutlak bir ana ihtiyaç duyduğu için {@link #closesAt} yine
     * de kolonda durur (her sorguda {@code opensAt + gün} hesaplamak
     * indekslenemez bir ifade üretirdi).
     */
    @Column(name = "duration_days")
    private Integer durationDays;

    /** null → sınırsız stant. */
    @Column(name = "max_listings")
    @Setter
    private Integer maxListings;

    /** true → başvuru anında onaylanır (küçük/güvenilir pazarlar için). */
    @Column(name = "auto_approve", nullable = false)
    @Setter
    private boolean autoApprove = false;

    /** Onaylı stant sayısı — denormalize, atomik UPDATE ile güncellenir. */
    @Column(name = "listing_count", nullable = false)
    private int listingCount = 0;

    public Marketplace(String slug, String name, Polygon boundary) {
        this.slug = slug;
        this.name = name;
        applyBoundary(boundary);
    }

    /**
     * Başlangıç anını ve süreyi birlikte belirler; kapanış anını <b>türetir</b>.
     *
     * <p>Kapanışın tek yazıldığı yer burasıdır — "başlangıç değişti ama kapanış
     * eski kaldı" durumu bu yüzden imkânsızdır.
     *
     * @param opensAt      başlangıç; {@code null} → hemen açık
     * @param durationDays kaç gün açık kalacak; {@code null} → süresiz
     */
    public void schedule(Instant opensAt, Integer durationDays) {
        this.opensAt = opensAt;
        this.durationDays = durationDays;
        /* ⚠️ Süre var ama başlangıç yoksa sayaç YARATILIŞ anından işler:
           "10 gün açık" diyen ama tarih vermeyen admin, bugünden itibaren 10
           gün kastediyor. Kapanışı null bırakmak pazarı sessizce süresiz
           yapardı — istenenin tam tersi. */
        Instant start = opensAt != null ? opensAt : getCreatedAt() != null ? getCreatedAt() : Instant.now();
        this.closesAt = durationDays == null ? null : start.plus(durationDays, ChronoUnit.DAYS);
    }

    /** Sınır değişince merkez ve alan da yeniden hesaplanır — üçü tutarlı kalır. */
    public void applyBoundary(Polygon boundary) {
        this.boundary = boundary;
        this.center = GeoUtils.point(
                boundary.getCentroid().getX(), boundary.getCentroid().getY());
        this.areaKm2 = GeoUtils.polygonAreaKm2(boundary);
    }

    /**
     * Başvuruya açık mı?
     *
     * Üç koşulun HEPSİ gerekir: durum OPEN · zaman penceresi içinde · kontenjan
     * dolmamış. Yalnız duruma bakmak, kapanış tarihi geçmiş bir pazarı açık
     * gösterirdi (durum değişimi zamanlanmış bir işe bağlı olurdu).
     */
    public boolean acceptsApplications(Instant now) {
        if (status != MarketplaceStatus.OPEN) {
            return false;
        }
        if (opensAt != null && now.isBefore(opensAt)) {
            return false;
        }
        if (closesAt != null && now.isAfter(closesAt)) {
            return false;
        }
        return maxListings == null || listingCount < maxListings;
    }

    /** Kontenjan doldu mu (kapanış sebebini ayırt edebilmek için ayrı). */
    public boolean isFull() {
        return maxListings != null && listingCount >= maxListings;
    }
}
