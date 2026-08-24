package com.waydee.marketplace.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.marketplace.domain.StoreProduct;
import com.waydee.marketplace.domain.StoreSettings;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * <b>Mağaza stüdyosu</b> — kabul edilen stant sahibinin 3B dükkânını
 * yönettiği panelin sözleşmesi.
 */
public final class StoreStudioDtos {

    private StoreStudioDtos() {
    }

    private static final String COLOR = "^#[0-9a-fA-F]{6}$";
    private static final String COLOR_MSG = "Renk #RRGGBB biçiminde olmalı";

    // ------------------------------------------------------------ ayarlar

    public record StudioSettingsRequest(
            @Size(max = 90) String displayName,

            /**
             * 🔴 15 Ağu 2026 — LOGO ARTIK STÜDYODAN DEĞİŞTİRİLİR.
             *
             * <p>Bildirilen hata: <i>"logo gelmiyor, sonrasında logo
             * değiştirince de değişmiyor."</i> Sebep: logo yalnız <b>başvuru
             * formunda</b> vardı ve başvuruyu düzenlemek stantı
             * {@code resubmit()} ile <b>PENDING</b>'e düşürüyor — yani mağaza
             * yeniden onaylanana kadar caddeden <b>tamamen kalkıyordu</b>.
             * Kullanıcının gördüğü "değişmedi" tam olarak buydu.
             *
             * <p>Logo mağazanın kendi markasıdır; onay gerektiren bir başvuru
             * verisi değil, sahibinin istediği zaman değiştirebileceği bir
             * sunum verisidir — yeri stüdyodur.
             *
             * <p>⚠️ Değer yine {@code marketplace_listings.logo_media_id}'ye
             * yazılır: 3B sahne ve liste kartı orayı okuyor. İkinci bir kolon
             * açmak, iki logonun ayrışması demekti.
             */
            UUID logoMediaId,

            UUID videoMediaId,
            Boolean tvEnabled,
            Boolean tvMuted,

            UUID musicMediaId,
            Boolean musicEnabled,
            @Min(0) @Max(100) Integer musicVolume,

            @Pattern(regexp = COLOR, message = COLOR_MSG) String lightColor,
            @Min(0) @Max(200) Integer lightIntensity,
            @Pattern(regexp = COLOR, message = COLOR_MSG) String accentColor,
            @Pattern(regexp = COLOR, message = COLOR_MSG) String facadeColor
    ) {
    }

    public record StudioSettingsView(
            UUID listingId,
            String displayName,
            /** Stantın logosu — stüdyodan değiştirilir, 3B tabelada görünür. */
            String logoUrl,
            UUID logoMediaId,
            String videoUrl,
            UUID videoMediaId,
            boolean tvEnabled,
            boolean tvMuted,
            String musicUrl,
            UUID musicMediaId,
            boolean musicEnabled,
            int musicVolume,
            String lightColor,
            int lightIntensity,
            String accentColor,
            String facadeColor
    ) {
        /**
         * @param logoMediaId stantın logosu — {@link StoreSettings}'te değil
         *                    {@code MarketplaceListing}'te durur, bu yüzden
         *                    dışarıdan geçirilir (tek kaynak korunur).
         */
        public static StudioSettingsView from(StoreSettings s, UUID logoMediaId) {
            return new StudioSettingsView(
                    s.getListingId(), s.getDisplayName(),
                    MediaUrls.of(logoMediaId), logoMediaId,
                    MediaUrls.of(s.getVideoMediaId()), s.getVideoMediaId(),
                    s.isTvEnabled(), s.isTvMuted(),
                    MediaUrls.of(s.getMusicMediaId()), s.getMusicMediaId(),
                    s.isMusicEnabled(), s.getMusicVolume(),
                    s.getLightColor(), s.getLightIntensity(),
                    s.getAccentColor(), s.getFacadeColor());
        }
    }

    // ------------------------------------------------------------ ürünler

    /**
     * Ürün ekleme/düzenleme.
     *
     * <p>⚠️ {@code postId} yalnız {@code source=POST} iken gönderilir; tutarlılık
     * hem serviste hem veritabanı kısıtında ({@code ck_store_product_post})
     * kontrol edilir.
     */
    public record StoreProductRequest(
            @NotNull(message = "Kaynak zorunludur")
            @Pattern(regexp = "POST|CUSTOM", message = "Kaynak POST ya da CUSTOM olmalı") String source,
            UUID postId,
            @NotBlank(message = "Ürün adı zorunludur") @Size(max = 140) String title,
            @Size(max = 500) String description,
            @DecimalMin(value = "0", message = "Fiyat negatif olamaz") BigDecimal price,
            @Pattern(regexp = "^[A-Z]{3}$", message = "Para birimi 3 harfli kod olmalı") String currency,
            @Size(max = 500) String productUrl,
            UUID imageMediaId,
            Boolean visible
    ) {
    }

    /** Raf sırasını toptan değiştirmek için — sürükle-bırak tek istekte kaydedilir. */
    public record ReorderRequest(
            @NotNull @Size(min = 1, max = 200, message = "En fazla 200 ürün sıralanabilir")
            List<@NotNull UUID> productIds
    ) {
    }

    public record StoreProductView(
            UUID id,
            String source,
            String sourceLabel,
            UUID postId,
            String title,
            String description,
            BigDecimal price,
            String currency,
            String productUrl,
            /** Görsel: CUSTOM'da yüklenen, POST'ta gönderinin ilk fotoğrafı. */
            String imageUrl,
            int position,
            boolean visible
    ) {
        public static StoreProductView from(StoreProduct p, String postImageUrl) {
            return new StoreProductView(
                    p.getId(), p.getSource().name(), p.getSource().label(), p.getPostId(),
                    p.getTitle(), p.getDescription(), p.getPrice(), p.getCurrency(),
                    p.getProductUrl(),
                    /* POST ürününde görsel gönderiden gelir; kopyalanmaz ki
                       kullanıcı fotoğrafı değiştirince raf da güncellensin. */
                    p.getPostId() != null ? postImageUrl : MediaUrls.of(p.getImageMediaId()),
                    p.getPosition(), p.isVisible());
        }
    }

    /** Stüdyo açılışında tek istekte gereken her şey. */
    public record StudioView(
            UUID listingId,
            String marketplaceSlug,
            String marketplaceName,
            String listingTitle,
            String status,
            StudioSettingsView settings,
            List<StoreProductView> products,
            /** Rafa eklenebilecek, henüz eklenmemiş gönderiler. */
            List<CandidatePost> candidates,
            int shelfCapacity
    ) {
    }

    /** Profilden rafa eklenebilecek bir gönderi. */
    public record CandidatePost(UUID postId, String imageUrl, String caption) {
    }
}
