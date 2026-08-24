package com.waydee.social.api.dto;

import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Harita bölge kartının TEK yükü.
 *
 * Kart eskiden üç ayrı isteğe (bölge feature'ı, profil, sahip) dağılmıştı ve
 * bazı alanlar (takipçi sayısı, bugünkü görüntülenme, kalan süre) hiç yoktu.
 * Burada tamamı tek çağrıda toplanır — kartın hiçbir alanı "yükleniyor"da
 * takılmaz ve N+1 istek olmaz.
 */
public final class TerritoryCardDtos {

    private TerritoryCardDtos() {
    }

    /** Kart üstündeki durum çipleri. */
    public record CardChip(
            /** ACTIVE | TREND | VERIFIED | RESERVED | EXPIRING | EXPIRED | LIVE */
            String code,
            String label,
            /** success | warn | info | danger | violet | neutral */
            String tone
    ) {
    }

    public record CardOwner(
            UUID id,
            String username,
            String displayName,
            String avatarUrl,
            long followerCount,
            long followingCount,
            boolean privateAccount,
            /** İstek yapan kişi bu sahibi takip ediyor mu (kimliksizse false). */
            boolean followedByMe,
            /** Takip isteği beklemede mi. */
            boolean followRequested,
            /** İstek yapan kişinin kendi bölgesi mi (Takip Et gizlenir). */
            boolean me
    ) {
    }

    public record CardStat(
            /** AREA | VALUE | LEASE | VIEWS */
            String code,
            String label,
            String value,
            String hint,
            /** Pastel ikon karosu tonu. */
            String tone
    ) {
    }

    @SuppressWarnings("java:S107")
    public record TerritoryCardResponse(
            UUID territoryId,
            String name,
            /** Konum etiketi: "Bölge · İl, Ülke". */
            String regionLabel,
            boolean verified,
            boolean reserved,
            String reservedLabel,
            List<CardChip> chips,
            CardOwner owner,
            String description,
            // ---- öne çıkan medya / canlı yayın
            String featuredMediaUrl,
            String liveUrl,
            boolean liveActive,
            // ---- ölçüler
            BigDecimal areaKm2,
            long areaM2,
            BigDecimal pricePaid,
            String currency,
            int radiusM,
            long viewsToday,
            long viewsTotal,
            int postCount,
            // ---- kiralama
            Instant purchasedAt,
            Instant leaseStartedAt,
            Instant expiresAt,
            long daysRemaining,
            boolean expired,
            int renewalCount,
            // ---- profil
            String profileType,
            String website,
            List<SocialLinkView> socialLinks,
            // ---- etkileşim
            int likeCount,
            int saveCount,
            boolean likedByMe,
            boolean savedByMe,
            /** İsteği yapan kişi bu dairenin SAHİBİ mi → düzenleme düğmeleri açılır. */
            boolean owned,
            /** Kart "Daireye Gir" düğmesini gösterebilir mi (gizli hesap kapatır). */
            boolean canOpen,
            /** Gizlilik/engel sebebiyle içerik kapalıysa gösterilecek gerekçe. */
            String lockedReason,
            List<CardStat> stats,
            /**
             * <b>Mağaza panelinin arka plan görseli</b> (V53). null → degrade.
             *
             * <p>⚠️ Gizli/kilitli kartta yazılmaz: kapak kişisel bir fotoğraf
             * olabilir ve kilit yalnız metni değil görseli de kapsamalı.
             */
            String coverUrl,
            /** Mağazanın kategorisi (V52) — panelde rozet olarak çizilir; null olabilir. */
            com.waydee.territory.api.dto.StoreCategoryDtos.StoreCategoryResponse category
    ) {
    }
}
