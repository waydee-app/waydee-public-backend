package com.waydee.territory.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.territory.domain.Territory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TerritoryDtos {

    private TerritoryDtos() {
    }

    /**
     * <b>Mağaza kurma isteği</b> (V38).
     *
     * <p>🔴 <b>Yarıçap ve süre alanı YOK.</b> Yarıçap sunucuda sabittir (100 m)
     * ve süre üyeliğe bağlıdır. İstemciden alınsalardı, sabit yarıçap ve
     * "üyelik boyunca geçerli" kuralı yalnızca arayüzde duran bir söz olurdu.
     */
    public record CreateStoreRequest(
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            /** Mağaza adı; boş bırakılırsa görünen ad kullanılır. */
            @Size(min = 2, max = 60, message = "Mağaza adı 2-60 karakter olmalı") String name,
            /** Kuruluş anında seçilen görünüm — sonradan da değiştirilebilir. */
            @Valid TerritoryStyleRequest style,
            /**
             * Mağazanın kategorisi (V52). Boş bırakılırsa kullanıcının
             * <b>kayıt sonrası verdiği cevaba</b> düşülür — bu yüzden alan
             * zorunlu değil: cevap zaten kullanıcıda duruyor olabilir.
             */
            UUID categoryId
    ) {
    }

    public record QuoteRequest(
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @Min(1) @Max(1_000_000) Integer radiusM,
            /**
             * Kiralama süresi (gün). Boş bırakılırsa 365 gün.
             *
             * <p>⚠️ Serbest sayı kabul edilmez: {@code LeaseDuration.ofDays}
             * tanımlı olmayan bir değeri varsayılana düşürür, aksi halde
             * çarpan tablosunun dışında bir süre satılabilirdi.
             */
            Integer days
    ) {
    }

    /** Süre seçeneği — satın alma ekranındaki tablo bunu çizer. */
    public record DurationOption(
            int days,
            BigDecimal totalPrice,
            /** Gün başına düşen bedel — "uzun al, ucuza gelsin" mesajı bununla verilir. */
            BigDecimal dailyPrice,
            /** 1 günlük orana göre indirim yüzdesi. */
            BigDecimal discountPercent
    ) {
    }

    public record QuoteResponse(
            boolean available,
            String reason,
            BigDecimal areaKm2,
            BigDecimal pricePerKm2,
            /** SEÇİLİ süre için ödenecek bedel. */
            BigDecimal totalPrice,
            String currency,
            String regionLabel,
            String countryName,
            String provinceName,
            String districtName,
            /** Seçili süre (gün). */
            int days,
            /**
             * Tüm süre seçenekleri, fiyatlarıyla birlikte.
             *
             * <p>⚠️ Tek istekte dönülür: kullanıcı süreyi değiştirdikçe
             * sunucuya gitmek hem yavaş hem gereksiz — fiyat yalnız alana ve
             * çarpana bağlı, ikisi de bu yanıtta var.
             */
            java.util.List<DurationOption> durations
    ) {
        public static QuoteResponse unavailable(String reason) {
            return new QuoteResponse(false, reason, null, null, null, null, null, null, null, null,
                    0, java.util.List.of());
        }
    }

    public record PurchaseRequest(
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @Min(1) @Max(1_000_000) Integer radiusM,
            @Size(min = 2, max = 60, message = "Alan adı 2-60 karakter olmalı") String name,
            @Size(max = 64) String idempotencyKey,
            /**
             * Kiralama süresi (gün) — boşsa 365.
             *
             * ⚠️ Sunucu bu değeri `LeaseDuration.ofDays` ile TANIMLI seçeneklere
             * indirger; istemciden gelen rastgele bir gün sayısı çarpan
             * tablosunun dışına düşemez.
             */
            Integer days,
            /** Satın alma anında seçilen görünüm (opsiyonel; sonradan da değiştirilebilir). */
            @Valid TerritoryStyleRequest style
    ) {
    }

    /**
     * Mağazanın görsel özelleştirmesi. Boş alanlar varsayılana düşer.
     *
     * <p>🔴 <b>21 Ağu 2026 — {@code markerStyle} eklendi (V51).</b> Harita
     * işaretçisi artık halkalı profil fotoğrafı; {@code strokeColor} halkanın
     * <b>rengi</b>, {@code markerStyle} ise <b>tasarımı</b> (nabız/parıltı/sakin).
     * İkisi ayrı kayıtlara bölünmedi: kullanıcı için tek bir şeyin — kendi
     * işaretçisinin — iki ayarı ve ikisi de aynı ekrandan geliyor.
     */
    public record TerritoryStyleRequest(
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Kenar rengi #RRGGBB olmalı") String strokeColor,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Dolgu rengi #RRGGBB olmalı") String fillColor,
            @DecimalMin("0") @DecimalMax("1") BigDecimal fillOpacity,
            @DecimalMin("0.5") @DecimalMax("8") BigDecimal strokeWidth,
            @Pattern(regexp = "NONE|FIRE|PULSE", message = "Geçersiz efekt") String effect,
            /** İşaretçi tasarımı: PULSE | GLOW | SOFT. Boş → değiştirme. */
            @Pattern(regexp = "PULSE|GLOW|SOFT", message = "Geçersiz işaretçi tasarımı") String markerStyle
    ) {
    }

    public record TerritoryStyleResponse(
            String strokeColor,
            String fillColor,
            BigDecimal fillOpacity,
            BigDecimal strokeWidth,
            String effect,
            /**
             * İşaretçi tasarımı — <b>her zaman dolu</b>, çünkü seçilmemiş olması
             * arayüzde "hiçbiri seçili değil" demek olurdu. Seçilmemişse
             * varsayılan döner.
             */
            String markerStyle
    ) {
        public static TerritoryStyleResponse from(Territory t) {
            return new TerritoryStyleResponse(t.getStrokeColor(), t.getFillColor(), t.getFillOpacity(),
                    t.getStrokeWidth(), t.getEffect() != null ? t.getEffect().name() : "NONE",
                    (t.getStoreMarkerStyle() != null
                            ? t.getStoreMarkerStyle()
                            : com.waydee.territory.domain.StoreMarkerStyle.DEFAULT).name());
        }
    }

    public record TerritoryResponse(
            UUID id,
            String name,
            UUID ownerId,
            String ownerUsername,
            String ownerDisplayName,
            String ownerAvatarUrl,
            double lng,
            double lat,
            int radiusM,
            BigDecimal areaKm2,
            BigDecimal pricePaid,
            String currency,
            String status,
            String regionLabel,
            Instant purchasedAt,
            // ---- kiralama
            Instant leaseStartedAt,
            Instant expiresAt,
            int leaseMonths,
            int renewalCount,
            long daysRemaining,
            boolean expired,
            TerritoryStyleResponse style,
            /**
             * Mağazanın kategorisi (V52) — {@code null} olabilir.
             *
             * <p>⚠️ Gömülü nesne, düz {@code categoryId} değil: düzenleme
             * ekranı ve mağaza kartı adı ve ikonu <b>anında</b> çizebilmeli.
             * Yalnız kimlik dönseydi her kart bir istek daha atardı.
             */
            StoreCategoryDtos.StoreCategoryResponse category,
            /** Mağaza panelinin arka plan görseli (V53); null → panel degrade çizer. */
            String coverUrl
    ) {
        public static TerritoryResponse from(Territory t, String regionLabel) {
            return from(t, regionLabel, null);
        }

        public static TerritoryResponse from(Territory t, String regionLabel,
                                             com.waydee.territory.domain.StoreCategory category) {
            var owner = t.getOwner();
            return new TerritoryResponse(
                    t.getId(),
                    t.getName(),
                    owner.getId(),
                    owner.getUsername(),
                    owner.getDisplayName(),
                    MediaUrls.of(owner.getAvatarMediaId()),
                    t.getCenter().getX(),
                    t.getCenter().getY(),
                    t.getRadiusM(),
                    t.getAreaKm2(),
                    t.getPricePaid(),
                    t.getCurrency(),
                    t.getStatus().name(),
                    regionLabel,
                    t.getPurchasedAt(),
                    t.getLeaseStartedAt(),
                    t.getExpiresAt(),
                    t.getLeaseMonths(),
                    t.getRenewalCount(),
                    t.daysRemaining(Instant.now()),
                    t.getStatus() == com.waydee.territory.domain.TerritoryStatus.EXPIRED,
                    TerritoryStyleResponse.from(t),
                    category != null ? StoreCategoryDtos.StoreCategoryResponse.from(category) : null,
                    MediaUrls.of(t.getStoreCoverMediaId()));
        }
    }

    /** Bölge adı + görünüm güncelleme (sahibi). */
    public record UpdateTerritoryRequest(
            @Size(min = 2, max = 60, message = "Alan adı 2-60 karakter olmalı") String name,
            @Valid TerritoryStyleRequest style,
            /**
             * Kategori (V52). {@code null} → <b>dokunma</b>.
             *
             * <p>🔴 "Kategoriyi kaldır" bu alanla ifade EDİLEMEZ, çünkü null
             * zaten "dokunma" demek. Kaldırma isteği bugün yok — kategori
             * seçildikten sonra başkasıyla değiştirilir. İhtiyaç doğarsa ayrı
             * bir bayrak gelmeli; null'a iki anlam yüklemek, adı boş gönderen
             * her istemcinin kategorisini sessizce silerdi.
             */
            UUID categoryId,
            /**
             * Kapak fotoğrafının medya kimliği (V53).
             *
             * <p>⚠️ {@code null} → <b>dokunma</b>. Kapağı KALDIRMAK için
             * {@link #clearCover} kullanılır — null'a iki anlam yüklenirse
             * yalnız adını değiştiren her istek kapağı da silerdi.
             */
            UUID coverMediaId,
            /** true → kapağı kaldır. {@code coverMediaId} ile birlikte gönderilirse kaldırma kazanır. */
            Boolean clearCover
    ) {
    }

    // ---------------------------------------------------------------- admin bölge yönetimi

    /**
     * Yönetim listesindeki bölge. Kullanıcı tarafına giden {@link TerritoryResponse}'tan
     * farkı: gizli/rezerve durumu ve sahip e-postası gibi yönetim alanlarını taşır.
     */
    public record AdminTerritoryResponse(
            UUID id,
            String name,
            UUID ownerId,
            String ownerUsername,
            String ownerDisplayName,
            String ownerAvatarUrl,
            double lng,
            double lat,
            int radiusM,
            BigDecimal areaKm2,
            BigDecimal pricePaid,
            String currency,
            String status,
            boolean hidden,
            boolean reserved,
            String reservedLabel,
            boolean verified,
            Instant purchasedAt,
            Instant expiresAt,
            long daysRemaining,
            int renewalCount,
            TerritoryStyleResponse style
    ) {
        public static AdminTerritoryResponse from(Territory t) {
            var owner = t.getOwner();
            return new AdminTerritoryResponse(
                    t.getId(),
                    t.getName(),
                    owner.getId(),
                    owner.getUsername(),
                    owner.getDisplayName(),
                    MediaUrls.of(owner.getAvatarMediaId()),
                    t.getCenter().getX(),
                    t.getCenter().getY(),
                    t.getRadiusM(),
                    t.getAreaKm2(),
                    t.getPricePaid(),
                    t.getCurrency(),
                    t.getStatus().name(),
                    t.isHidden(),
                    t.isReserved(),
                    t.getReservedLabel(),
                    t.isVerified(),
                    t.getPurchasedAt(),
                    t.getExpiresAt(),
                    t.daysRemaining(Instant.now()),
                    t.getRenewalCount(),
                    TerritoryStyleResponse.from(t));
        }
    }

    /**
     * Admin bölge güncellemesi. Tüm alanlar opsiyoneldir; yalnız verilenler uygulanır
     * (null = "dokunma"). Sahip devri {@code ownerId} ile yapılır.
     */
    public record AdminUpdateTerritoryRequest(
            @Size(min = 2, max = 60, message = "Alan adı 2-60 karakter olmalı") String name,
            UUID ownerId,
            Boolean hidden,
            Boolean reserved,
            @Size(max = 80) String reservedLabel,
            /** ACTIVE | REVOKED — pasife alma / geri getirme. */
            @Pattern(regexp = "ACTIVE|REVOKED", message = "Geçersiz durum") String status,
            /** "Doğrulanmış Alan" rozeti — yalnız yönetim verir. */
            Boolean verified,
            /** Kirayı elle uzat (ay). Yönetim jesti; ödeme alınmaz. */
            @Min(1) @Max(120) Integer extendLeaseMonths,
            @Valid TerritoryStyleRequest style
    ) {
    }

    /** Admin'in haritada çizdiği rezerve (sahibi belirsiz, kurumsal) alan. */
    public record AdminReserveRequest(
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @Min(1) @Max(1_000_000) Integer radiusM,
            @NotNull @Size(min = 2, max = 60, message = "Alan adı 2-60 karakter olmalı") String name,
            @Size(max = 80) String reservedLabel,
            @Valid TerritoryStyleRequest style
    ) {
    }
}
