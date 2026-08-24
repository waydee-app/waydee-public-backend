package com.waydee.territory.domain;

import com.waydee.common.geo.GeoUtils;
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
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "territories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Territory extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false, length = 60)
    @Setter
    private String name;

    @Column(name = "center", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point center;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(name = "boundary", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    @Column(name = "area_km2", nullable = false, precision = 12, scale = 4)
    private BigDecimal areaKm2;

    @Column(name = "price_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePaid;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "province_id")
    private UUID provinceId;

    @Column(name = "district_id")
    private UUID districtId;

    /** Satın alma anında fiyatı belirleyen serbest çizim bölgesi (varsa). */
    @Column(name = "pricing_zone_id")
    private UUID pricingZoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private TerritoryStatus status;

    /** İlk alım anı — yenilemede DEĞİŞMEZ (geçmiş ve "üyelik yaşı" buna bakar). */
    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    // ---------------------------------------------------------------- kiralama
    /** Yürürlükteki kiralama döneminin başlangıcı (yenilemede ileri kayar). */
    @Column(name = "lease_started_at", nullable = false)
    private Instant leaseStartedAt;

    /** Yürürlükteki dönemin bitişi. Geçilince bölge EXPIRED'a düşer. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Dönem uzunluğu (ay). Varsayılan 12 — "1 yıllık kiralık". */
    @Column(name = "lease_months", nullable = false)
    private int leaseMonths = DEFAULT_LEASE_MONTHS;

    /**
     * Kira süresi <b>gün</b> cinsinden.
     *
     * ⚠️ `leaseMonths` yetmiyor: 7 günlük bir kira ay cinsinden ifade
     * edilemez (0 ay?). Gün alanı asıl kaynaktır; `leaseMonths` eski
     * kayıtlar ve fatura metinleri için kalır.
     */
    @Column(name = "lease_days", nullable = false)
    private int leaseDays = DEFAULT_LEASE_DAYS;

    /** Kaç kez yenilendi (0 = hiç). */
    @Column(name = "renewal_count", nullable = false)
    private int renewalCount = 0;

    public static final int DEFAULT_LEASE_MONTHS = 12;
    /** Varsayılan kira: bir yıl. */
    public static final int DEFAULT_LEASE_DAYS = 365;

    // ---------------------------------------------------------------- görünüm
    /** Daire kenar rengi (#RRGGBB). null → sahibe göre otomatik renk. */
    @Column(name = "stroke_color", length = 9)
    @Setter
    private String strokeColor;

    /** Daire iç dolgu rengi (#RRGGBB). null → kenar rengiyle aynı. */
    @Column(name = "fill_color", length = 9)
    @Setter
    private String fillColor;

    /** Dolgu opaklığı 0–1. null → varsayılan 0.38. */
    @Column(name = "fill_opacity", precision = 3, scale = 2)
    @Setter
    private BigDecimal fillOpacity;

    /** Kenar kalınlığı (px). null → varsayılan 2.5. */
    @Column(name = "stroke_width", precision = 3, scale = 1)
    @Setter
    private BigDecimal strokeWidth;

    /** Özel efekt: NONE | FIRE | PULSE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 20)
    @Setter
    private TerritoryEffect effect = TerritoryEffect.NONE;

    /**
     * Haritadaki işaretçinin tasarımı (V51). null → {@link StoreMarkerStyle#DEFAULT}.
     *
     * <p>⚠️ Toplu güncelleme yapılmadı; NULL <b>"kullanıcı seçmedi"</b>
     * demektir ve okuma tarafında varsayılana düşer. Satırlara varsayılanı
     * yazmak aynı görüntüyü verir ama seçenlerle seçmeyenleri ayırt etme
     * bilgisini kaybederdi — varsayılan ileride değişirse seçmemiş olanlar
     * yeni varsayılana taşınabilmeli.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "store_marker_style", length = 20)
    @Setter
    private StoreMarkerStyle storeMarkerStyle;

    /**
     * <b>Mağazanın kategorisi</b> (V52). null → kategorisiz.
     *
     * <p>🔴 <b>Neden {@code @ManyToOne} DEĞİL, düz {@code UUID}:</b> bu alanı
     * okuyan asıl yer haritanın GeoJSON'unu üreten döngüdür ve orada
     * <b>bütün</b> mağazalar dolaşılır. Tembel bir ilişki her satırda bir
     * sorgu daha (N+1), hevesli bir ilişki her satırda bir JOIN daha demekti.
     * Kategori sayısı onlarla ölçülür: liste <b>bir kez</b> okunup bir haritaya
     * çevriliyor ve satırlar oradan çözülüyor. Aynı gerekçe V50'de de
     * yazılıydı — harita sorgusuna JOIN eklememek bu şemanın kuralı.
     *
     * <p>⚠️ Bütünlüğü veritabanındaki yabancı anahtar korur
     * ({@code ON DELETE RESTRICT}), nesne grafiği değil.
     */
    @Column(name = "category_id")
    @Setter
    private UUID categoryId;

    /**
     * <b>Mağaza panelinin arka plan (kapak) fotoğrafı</b> (V53). null → degrade.
     *
     * <p>⚠️ Medya <b>kimliği</b> tutulur, adres değil: adresler imzalıdır ve
     * süresi dolar; satıra yazılan bir adres birkaç saat sonra ölürdü.
     */
    @Column(name = "store_cover_media_id")
    @Setter
    private UUID storeCoverMediaId;

    // ---------------------------------------------------------------- yönetim (admin)
    /** true → bölge haritada ve public uçlarda görünmez (silinmez, gizlenir). */
    @Column(name = "hidden", nullable = false)
    @Setter
    private boolean hidden = false;

    /**
     * true → "rezerve" bölge: sahip kimliği dışarı verilmez, kurumsal etiketle
     * gösterilir. Teknik sahibi rezerveyi oluşturan admin kullanıcısıdır.
     */
    @Column(name = "reserved", nullable = false)
    @Setter
    private boolean reserved = false;

    /** Rezerve bölgede gösterilecek etiket; null → "Rezerve alan". */
    @Column(name = "reserved_label", length = 80)
    @Setter
    private String reservedLabel;

    /**
     * "Doğrulanmış Alan" rozeti. YALNIZ admin verir — kullanıcının kendi
     * bölgesini doğrulaması rozetin anlamını yok ederdi.
     */
    @Column(name = "verified", nullable = false)
    @Setter
    private boolean verified = false;

    // ---------------------------------------------------------------- sosyal sayaçlar
    /** Dairenin kendisine verilen beğeni (gönderi beğenisinden ayrı). Atomik güncellenir. */
    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    /** Kaç kişi kaydetti (yer imi). */
    @Column(name = "save_count", nullable = false)
    private int saveCount = 0;

    /** Admin bölge sahipliğini devrederken kullanılır. */
    public void reassignOwner(User newOwner) {
        this.owner = newOwner;
    }

    public Territory(User owner, String name, double lng, double lat, int radiusM,
                     BigDecimal pricePaid, String currency,
                     UUID countryId, UUID provinceId, UUID districtId, UUID pricingZoneId) {
        this.owner = owner;
        this.name = name;
        this.center = GeoUtils.point(lng, lat);
        this.radiusM = radiusM;
        this.boundary = GeoUtils.circle(lng, lat, radiusM);
        this.areaKm2 = GeoUtils.circleAreaKm2(radiusM);
        this.pricePaid = pricePaid;
        this.currency = currency;
        this.countryId = countryId;
        this.provinceId = provinceId;
        this.districtId = districtId;
        this.pricingZoneId = pricingZoneId;
        this.status = TerritoryStatus.ACTIVE;
        this.purchasedAt = Instant.now();
        this.leaseStartedAt = this.purchasedAt;
        this.leaseMonths = DEFAULT_LEASE_MONTHS;
        this.leaseDays = DEFAULT_LEASE_DAYS;
        this.expiresAt = this.leaseStartedAt.plus(java.time.Duration.ofDays(DEFAULT_LEASE_DAYS));
    }

    // ---------------------------------------------------------------- kiralama davranışı

    /** Süresi geçmiş mi (durumdan bağımsız takvim kontrolü). */
    public boolean isLapsed(Instant now) {
        return expiresAt.isBefore(now);
    }

    /**
     * Kalan gün — geçmişse 0.
     *
     * ⚠️ YUKARI yuvarlanır. Kesme kullanılırsa 1 yıllık kira satın alındığı an
     * "364 gün kaldı" yazar (365 günden milisaniyeler eksiktir) ve kullanıcıya
     * bir gün eksik satılmış gibi görünür. Bugün biten kira "1 gün" gösterir,
     * bu da doğru okunuştur: gün henüz dolmamıştır.
     */
    public long daysRemaining(Instant now) {
        java.time.Duration left = java.time.Duration.between(now, expiresAt);
        if (left.isNegative() || left.isZero()) {
            return 0;
        }
        return (left.toMillis() + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY;
    }

    private static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

    /**
     * Dönemi uzatır.
     *
     * ⚠️ Yeni dönem, <b>mevcut bitişten</b> başlar (henüz süresi dolmamışsa) —
     * erken yenileyen kullanıcı kalan günlerini kaybetmez. Süresi çoktan
     * dolmuşsa geçmişe dönem yazmamak için <b>şimdiden</b> başlar.
     */
    public void renew(int months, Instant now) {
        renewDays(months * 30, now);
    }

    /**
     * Dönemi GÜN cinsinden uzatır — süreli satışın kullandığı yol.
     *
     * ⚠️ Yeni dönem mevcut bitişten başlar (erken yenileyen gün kaybetmez);
     * süresi çoktan dolmuşsa şimdiden başlar.
     */
    public void renewDays(int days, Instant now) {
        Instant base = expiresAt.isAfter(now) ? expiresAt : now;
        this.leaseStartedAt = base;
        this.leaseDays = days;
        this.leaseMonths = Math.max(1, Math.round(days / 30f));
        this.expiresAt = base.plus(java.time.Duration.ofDays(days));
        this.renewalCount += 1;
        this.status = TerritoryStatus.ACTIVE;
    }

    /**
     * İlk kiralama süresini gün cinsinden ayarlar (satın alma anında).
     *
     * ⚠️ Kurucu varsayılanı 365 gün yazar; seçilen süre farklıysa bu metotla
     * düzeltilir. Ayrı metot olması bilinçli: kurucuya bir parametre daha
     * eklemek tüm çağrı yerlerini kırardı ve rezerve alan/yönetici oluşturma
     * yolları süre seçmiyor.
     */
    /**
     * Bitişi verilen ana <b>çeker</b> — mağazanın ömrünü üyeliğe bağlar (V38).
     *
     * <p>⚠️ Yalnız <b>ileri</b> taşır: geriye çekmek, üyeliğini yenileyen ama
     * planı kısa bir dönemle uzayan kullanıcının mağazasını erkene alırdı.
     * Süresi dolup EXPIRED'a düşmüş mağaza yeniden ACTIVE olur — üyelik geri
     * geldiyse mağaza da geri gelmeli.
     */
    public void extendUntil(Instant until) {
        if (until == null || !until.isAfter(this.expiresAt)) {
            return;
        }
        this.expiresAt = until;
        this.leaseDays = (int) Math.max(1, java.time.Duration.between(this.leaseStartedAt, until).toDays());
        this.leaseMonths = Math.max(1, Math.round(this.leaseDays / 30f));
        if (this.status == TerritoryStatus.EXPIRED) {
            this.status = TerritoryStatus.ACTIVE;
        }
    }

    public void applyLeaseDays(int days) {
        this.leaseDays = days;
        this.leaseMonths = Math.max(1, Math.round(days / 30f));
        this.expiresAt = this.leaseStartedAt.plus(java.time.Duration.ofDays(days));
    }

    /** Süresi dolan bölgeyi haritadan düşürür (veri silinmez). */
    public void markExpired() {
        this.status = TerritoryStatus.EXPIRED;
    }

    /** Ay ekleme takvim tabanlıdır (30 gün değil) — 29 Şubat gibi günler kaymasın. */
    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(java.time.ZoneOffset.UTC).plusMonths(months).toInstant();
    }
}
