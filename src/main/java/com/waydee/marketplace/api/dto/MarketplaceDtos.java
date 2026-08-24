package com.waydee.marketplace.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.marketplace.domain.ListingCategory;
import com.waydee.marketplace.domain.ListingStage;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceListing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MarketplaceDtos {

    private MarketplaceDtos() {
    }

    // ======================================================== pazar yeri (admin girdi)

    public record MarketplaceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 60) @Pattern(regexp = "^[a-z0-9-]*$",
                    message = "Kısa ad yalnız küçük harf, rakam ve tire içerebilir") String slug,
            @Size(max = 200) String tagline,
            @Size(max = 2000) String description,

            /** Serbest çizim halkası: [[lng, lat], ...] — en az 3 nokta. */
            @NotNull @NotEmpty(message = "Pazar yeri sınırı çizilmeli")
            @Size(min = 3, max = 500, message = "Poligon 3-500 nokta içermeli")
            List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> ring,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Renk #RRGGBB olmalı") String accentColor,
            UUID coverMediaId,
            @Pattern(regexp = "DRAFT|OPEN|CLOSED|ARCHIVED", message = "Geçersiz durum") String status,
            /** Pazarın açılacağı an; null → hemen. */
            Instant opensAt,
            /**
             * 🔴 Kaç gün açık kalacak (kullanıcı isteği). Kapanış anı bundan
             * TÜRETİLİR — istemci `closesAt` göndermez, yalnız okur.
             * ⚠️ Tavan 3650 gün (10 yıl): süresiz istenen yerde alan boş
             * bırakılır, saçma büyük bir sayı yazılmaz.
             */
            @Min(value = 1, message = "Süre en az 1 gün olmalı")
            @Max(value = 3650, message = "Süre en fazla 3650 gün olabilir")
            Integer durationDays,
            @Min(1) @Max(10_000) Integer maxListings,
            Boolean autoApprove,
            /** GENERAL | STARTUP | LISTING | EVENT | TOUR | WALK | FOOD | ART */
            @Pattern(regexp = "GENERAL|STARTUP|LISTING|EVENT|TOUR|WALK|FOOD|ART",
                    message = "Geçersiz pazar türü") String kind,
            /** Adminin tasarladığı form; null → türün varsayılanı. */
            @Valid FormSchemaInput formSchema,
            @Size(max = 1000) String applicationNote
    ) {
    }

    // ---- form şeması (admin girdisi)

    public record FieldConfigInput(
            @NotBlank String field,
            boolean enabled,
            boolean required,
            @Size(max = 60) String label,
            @Size(max = 160) String help
    ) {
    }

    public record QuestionInput(
            @NotBlank @Size(max = 40) @Pattern(regexp = "^[a-z0-9_]+$",
                    message = "Anahtar yalnız küçük harf, rakam ve alt çizgi içerebilir") String key,
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Pattern(regexp = "TEXT|TEXTAREA|NUMBER|SELECT|CHECKBOX|DATE",
                    message = "Geçersiz soru tipi") String type,
            boolean required,
            @Size(max = 160) String help,
            @Size(max = 20) List<@Size(max = 60) String> options,
            @Min(1) @Max(3000) Integer maxLength
    ) {
    }

    public record FormSchemaInput(
            @Size(max = 40) @Valid List<FieldConfigInput> fields,
            @Size(max = 15, message = "En fazla 15 ek soru eklenebilir") @Valid List<QuestionInput> questions
    ) {
    }

    /** İstemcinin formu çizebilmesi için çözülmüş şema (varsayılanlar uygulanmış). */
    public record ResolvedField(String field, boolean enabled, boolean required, String label, String help) {
    }

    public record ResolvedQuestion(String key, String label, String type, boolean required,
                                   String help, List<String> options, Integer maxLength) {
    }

    public record ResolvedForm(String kind, String kindLabel, String applicationNote,
                               List<ResolvedField> fields, List<ResolvedQuestion> questions) {
    }

    public record KindOption(String code, String label, String hint, List<String> defaultFields) {
    }

    // ======================================================== pazar yeri (çıktı)

    public record MarketplaceResponse(
            UUID id,
            String slug,
            String name,
            String tagline,
            String description,
            List<List<Double>> ring,
            double centerLng,
            double centerLat,
            BigDecimal areaKm2,
            String accentColor,
            String coverUrl,
            String status,
            Instant opensAt,
            /** Türetilmiş — admin ekranı bunu okur ama göndermez. */
            Instant closesAt,
            Integer durationDays,
            Integer maxListings,
            boolean autoApprove,
            int listingCount,
            /** Şu an başvuru alıyor mu (durum + pencere + kontenjan birlikte). */
            boolean acceptingApplications,
            boolean full,
            /** İstek yapan üyenin bu pazardaki başvuru durumu (yoksa null). */
            String myListingStatus,
            UUID myListingId
    ) {
        public static MarketplaceResponse from(Marketplace m, boolean accepting,
                                               String myStatus, UUID myListingId) {
            return new MarketplaceResponse(
                    m.getId(), m.getSlug(), m.getName(), m.getTagline(), m.getDescription(),
                    ringOf(m), m.getCenter().getX(), m.getCenter().getY(), m.getAreaKm2(),
                    m.getAccentColor(), MediaUrls.of(m.getCoverMediaId()), m.getStatus().name(),
                    m.getOpensAt(), m.getClosesAt(), m.getDurationDays(),
                    m.getMaxListings(), m.isAutoApprove(),
                    m.getListingCount(), accepting, m.isFull(), myStatus, myListingId);
        }

        private static List<List<Double>> ringOf(Marketplace m) {
            var coords = m.getBoundary().getExteriorRing().getCoordinates();
            // Son nokta ilkiyle aynıdır (kapalı halka) — istemciye tekrar gönderilmez.
            return java.util.Arrays.stream(coords, 0, Math.max(coords.length - 1, 0))
                    .map(c -> List.of(c.x, c.y))
                    .toList();
        }
    }

    // ======================================================== stant (üye girdi)

    /**
     * 🔴 15 Ağu 2026 — BAŞVURU DÖRT ALANA İNDİ.
     *
     * <p>Kullanıcı isteği: <i>"alt kısımdaki formu da sadece tek cümlelik
     * tanıtım, logo, website, telefon no olacak şekilde alacağız."</i>
     *
     * <p>Zorunlu olan tek şey <b>tanıtım cümlesi</b> ({@code tagline});
     * logo/website/telefon isteğe bağlı.
     *
     * <h2>⚠️ `title` (mağaza adı) neden hâlâ burada ama zorunlu değil</h2>
     * <p>Mağaza adı 3B tabelanın ve liste kartının tek kaynağıdır — onsuz sahne
     * çizilemez. Ama form artık sormuyor; gelmezse {@code ListingService} onu
     * kullanıcının <b>görünen adından türetiyor</b> ve sahibi kabul edildikten
     * sonra <b>stüdyodan</b> değiştiriyor. Böylece hem "sadece dört alan"
     * isteği karşılanıyor hem de sahne bozulmuyor.
     *
     * <h2>⚠️ Eski alanlar neden silinmedi</h2>
     * <p>{@code description}, {@code category}, {@code stage}, fiyat/etkinlik
     * alanları vb. <b>duruyor ama hiçbiri zorunlu değil</b>. Pazar türüne göre
     * çalışan eski ekranlar (STARTUP · EVENT · LISTING) ve mevcut başvurular
     * bunları okuyor; alanı DTO'dan atmak o akışları sessizce bozardı.
     * Yeni mağaza formu bunları hiç göndermez.
     */
    public record ListingRequest(
            @Size(max = 90) String title,
            @NotBlank(message = "Tek cümlelik tanıtım zorunludur")
            @Size(max = 140, message = "Tanıtım en fazla 140 karakter olabilir") String tagline,
            @Size(max = 3000) String description,
            @Pattern(regexp = "STARTUP|ECOMMERCE|FOOD|ART|SERVICE|TECH|EDUCATION|HEALTH|TRAVEL|OTHER",
                    message = "Geçersiz kategori") String category,
            @Pattern(regexp = "IDEA|MVP|EARLY_REVENUE|GROWTH|ESTABLISHED",
                    message = "Geçersiz aşama") String stage,
            @Size(max = 300) String website,
            @Email(message = "Geçerli bir e-posta girin") @Size(max = 255) String contactEmail,
            UUID logoMediaId,
            UUID coverMediaId,
            @Min(1800) @Max(2200) Integer foundedYear,
            @Min(1) @Max(100_000) Integer teamSize,
            @Size(max = 300) String lookingFor,
            // ---- türe göre alanlar
            java.time.Instant startsAt,
            java.time.Instant endsAt,
            @Size(max = 200) String locationLabel,
            @Min(1) @Max(1_000_000) Integer capacity,
            @jakarta.validation.constraints.DecimalMin("0") BigDecimal price,
            @Pattern(regexp = "^[A-Z]{3}$", message = "Para birimi 3 harfli kod olmalı") String currency,
            @Pattern(regexp = "NEW|LIKE_NEW|GOOD|USED|FOR_PARTS", message = "Geçersiz durum") String conditionCode,
            @Size(max = 30) String contactPhone,
            @Size(max = 8, message = "En fazla 8 görsel eklenebilir") List<UUID> galleryMediaIds,
            /** Adminin tanımladığı serbest soruların cevapları. */
            java.util.Map<String, String> customFields
    ) {
    }

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED", message = "Karar APPROVED ya da REJECTED olmalı")
            String decision,
            @Size(max = 500) String note
    ) {
    }

    // ======================================================== stant (çıktı)

    public record ListingOwner(UUID id, String username, String displayName, String avatarUrl) {
    }

    @SuppressWarnings("java:S107")
    public record ListingResponse(
            UUID id,
            UUID marketplaceId,
            String marketplaceName,
            String marketplaceSlug,
            ListingOwner owner,
            String title,
            String tagline,
            String description,
            String category,
            String categoryLabel,
            String stage,
            String stageLabel,
            String website,
            String contactEmail,
            String logoUrl,
            String coverUrl,
            Integer foundedYear,
            Integer teamSize,
            String lookingFor,
            Double spotLng,
            Double spotLat,
            String status,
            Instant submittedAt,
            Instant reviewedAt,
            String reviewNote,
            boolean featured,
            int viewCount,
            int likeCount,
            boolean likedByMe,
            boolean mine,
            // ---- türe göre alanlar
            Instant startsAt,
            Instant endsAt,
            String locationLabel,
            Integer capacity,
            BigDecimal price,
            String currency,
            String conditionCode,
            String contactPhone,
            List<String> galleryUrls,
            java.util.Map<String, String> customFields,
            String marketplaceKind
    ) {
        public static ListingResponse from(MarketplaceListing l, Marketplace m,
                                           boolean likedByMe, boolean mine) {
            return from(l, m, likedByMe, mine, java.util.Map.of());
        }

        public static ListingResponse from(MarketplaceListing l, Marketplace m,
                                           boolean likedByMe, boolean mine,
                                           java.util.Map<String, String> customFields) {
            var o = l.getOwner();
            return new ListingResponse(
                    l.getId(), l.getMarketplaceId(),
                    m != null ? m.getName() : null,
                    m != null ? m.getSlug() : null,
                    new ListingOwner(o.getId(), o.getUsername(), o.getDisplayName(),
                            MediaUrls.of(o.getAvatarMediaId())),
                    l.getTitle(), l.getTagline(), l.getDescription(),
                    l.getCategory().name(), l.getCategory().label(),
                    l.getStage() != null ? l.getStage().name() : null,
                    l.getStage() != null ? l.getStage().label() : null,
                    l.getWebsite(), l.getContactEmail(),
                    MediaUrls.of(l.getLogoMediaId()), MediaUrls.of(l.getCoverMediaId()),
                    l.getFoundedYear(), l.getTeamSize(), l.getLookingFor(),
                    l.getSpot() != null ? l.getSpot().getX() : null,
                    l.getSpot() != null ? l.getSpot().getY() : null,
                    l.getStatus().name(), l.getSubmittedAt(), l.getReviewedAt(), l.getReviewNote(),
                    l.isFeatured(), l.getViewCount(), l.getLikeCount(), likedByMe, mine,
                    l.getStartsAt(), l.getEndsAt(), l.getLocationLabel(), l.getCapacity(),
                    l.getPrice(), l.getCurrency(), l.getConditionCode(), l.getContactPhone(),
                    l.getGalleryMediaIds() == null ? List.of()
                            : java.util.Arrays.stream(l.getGalleryMediaIds())
                                    .map(MediaUrls::of).filter(java.util.Objects::nonNull).toList(),
                    customFields,
                    m != null ? m.getKind().name() : null);
        }
    }

    /** Seçenek listeleri — istemci sabit kodlamasın diye sunucudan gelir. */
    public record OptionView(String code, String label) {
        public static List<OptionView> categories() {
            return java.util.Arrays.stream(ListingCategory.values())
                    .map(c -> new OptionView(c.name(), c.label())).toList();
        }

        public static List<OptionView> stages() {
            return java.util.Arrays.stream(ListingStage.values())
                    .map(s -> new OptionView(s.name(), s.label())).toList();
        }
    }
}
