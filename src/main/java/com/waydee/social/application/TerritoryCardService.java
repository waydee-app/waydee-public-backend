package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView;
import com.waydee.identity.application.BlockService;
import com.waydee.identity.application.SocialLinkService;
import com.waydee.identity.domain.FollowStatus;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.FollowRepository;
import com.waydee.social.api.dto.TerritoryCardDtos.CardChip;
import com.waydee.social.api.dto.TerritoryCardDtos.CardOwner;
import com.waydee.social.api.dto.TerritoryCardDtos.CardStat;
import com.waydee.social.api.dto.TerritoryCardDtos.TerritoryCardResponse;
import com.waydee.social.domain.TerritoryProfile;
import com.waydee.social.infrastructure.TerritoryProfileRepository;
import com.waydee.social.infrastructure.TerritoryViewRepository;
import com.waydee.territory.application.TerritoryService;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.domain.TerritoryStatus;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Harita bölge kartını TEK sorgu kümesinde toplar.
 *
 * Kart, uygulamanın en çok açılan yüzeyidir; bu yüzden burada N+1 yoktur —
 * her sayaç tek bir COUNT'tur ve sosyal bağlantılar tek çağrıda gelir.
 *
 * <p><b>Gizlilik:</b> viewer {@code null} olabilir (vitrin). Gizli hesabın ya da
 * engelli kullanıcının kartı yine döner ama {@code canOpen=false} ve
 * {@code lockedReason} dolu gelir — kart "yok" olmaz, sadece kapısı kapanır.
 * Rezerve bölgede sahip kimliği hiç üretilmez.
 */
@Service
@RequiredArgsConstructor
public class TerritoryCardService {

    /** Bu eşiği aşan günlük görüntülenme "Trend" rozeti kazandırır. */
    private static final long TREND_VIEWS_TODAY = 25;
    /** Kalan gün bu sınırın altına inince kartta uyarı çipi çıkar. */
    private static final long EXPIRING_SOON_DAYS = 30;

    private final TerritoryRepository territoryRepository;
    /** Kartın kategori rozeti (V52) — kimlikten adı/ikonu çözer. */
    private final com.waydee.territory.infrastructure.StoreCategoryRepository storeCategoryRepository;
    private final TerritoryProfileRepository profileRepository;
    private final TerritoryViewRepository viewRepository;
    private final FollowRepository followRepository;
    private final SocialLinkService socialLinkService;
    private final ContentAccessService contentAccessService;
    private final BlockService blockService;
    private final TerritoryService territoryService;
    private final TerritoryEngagementService engagementService;
    private final com.waydee.social.infrastructure.PostRepository postRepository;

    @Transactional(readOnly = true)
    public TerritoryCardResponse card(UUID territoryId, UUID viewerId) {
        Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        // Gizlenmiş bölge hiç görünmez (admin gizlemesi kartı da kapatır).
        if (territory.isHidden()) {
            throw ApiException.notFound("Alan bulunamadı");
        }
        return build(territory, viewerId);
    }

    @Transactional(readOnly = true)
    public TerritoryCardResponse build(Territory territory, UUID viewerId) {
        User owner = territory.getOwner();
        boolean reserved = territory.isReserved();
        Instant now = Instant.now();
        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);

        TerritoryProfile profile = profileRepository.findById(territory.getId()).orElse(null);

        long viewsToday = viewRepository.countByTerritoryIdAndViewedAtAfter(territory.getId(), startOfDay);
        long viewsTotal = viewRepository.countByTerritoryId(territory.getId());

        // ---- erişim kararı (engel gizlilikten ÖNCE gelir)
        String lockedReason = null;
        boolean canOpen = true;
        if (!reserved) {
            if (viewerId != null && blockService.blockedBetween(viewerId, owner.getId())) {
                canOpen = false;
                lockedReason = "Bu kullanıcıyla etkileşim kurulamıyor";
            } else if (!contentAccessService.canView(viewerId, owner)) {
                canOpen = false;
                lockedReason = "Bu hesap gizli";
            }
        }

        // ---- sahip bloğu (rezerve bölgede kimlik ÜRETİLMEZ)
        CardOwner cardOwner;
        if (reserved) {
            cardOwner = new CardOwner(null, "waydee",
                    territory.getReservedLabel() != null ? territory.getReservedLabel() : "Rezerve alan",
                    null, 0, 0, false, false, false, false);
        } else {
            boolean me = viewerId != null && viewerId.equals(owner.getId());
            boolean followed = viewerId != null && !me && followRepository
                    .existsByFollowerIdAndFolloweeIdAndStatus(viewerId, owner.getId(), FollowStatus.ACCEPTED);
            boolean requested = viewerId != null && !me && !followed && followRepository
                    .existsByFollowerIdAndFolloweeIdAndStatus(viewerId, owner.getId(), FollowStatus.PENDING);
            cardOwner = new CardOwner(
                    owner.getId(), owner.getUsername(), owner.getDisplayName(),
                    MediaUrls.of(owner.getAvatarMediaId()),
                    followRepository.countByFolloweeIdAndStatus(owner.getId(), FollowStatus.ACCEPTED),
                    followRepository.countByFollowerIdAndStatus(owner.getId(), FollowStatus.ACCEPTED),
                    owner.isPrivateAccount(), followed, requested, me);
        }

        // ---- sosyal bağlantılar: yalnız açık profilde ve izin varsa
        List<SocialLinkView> socialLinks = List.of();
        if (!reserved && canOpen && profile != null && profile.isShowSocialLinks()) {
            socialLinks = socialLinkService.list(owner.getId());
        }

        var engagement = engagementService.state(territory.getId(), viewerId);
        long daysRemaining = territory.daysRemaining(now);
        boolean expired = territory.getStatus() == TerritoryStatus.EXPIRED || daysRemaining <= 0;

        return new TerritoryCardResponse(
                territory.getId(),
                territory.getName(),
                territoryService.resolveRegionLabel(territory),
                territory.isVerified(),
                reserved,
                territory.getReservedLabel(),
                chips(territory, viewsToday, daysRemaining, expired, profile),
                cardOwner,
                canOpen && profile != null ? profile.getDescription() : null,
                canOpen && profile != null ? MediaUrls.of(profile.getFeaturedMediaId()) : null,
                canOpen && profile != null ? profile.getLiveUrl() : null,
                canOpen && profile != null && profile.isLiveActive() && profile.getLiveUrl() != null,
                territory.getAreaKm2(),
                areaM2(territory.getAreaKm2()),
                territory.getPricePaid(),
                territory.getCurrency(),
                territory.getRadiusM(),
                viewsToday,
                viewsTotal,
                /* 🔴 SAHİBİN gönderi sayısı — bölgeninki DEĞİL.
                   `profile.getPostCount()` yalnız BÖLGEYE bağlı gönderileri
                   sayıyordu; gönderiler profil akışından açıldığı için
                   `posts.territory_id` NULL kalıyor (ölçüldü: 5 gönderinin
                   5'i de bölgesiz). Sonuç: mağaza kartı gönderisi olan
                   kullanıcıda bile "0" ve "Henüz gönderi yok" gösteriyordu. */
                reserved ? 0 : (int) postRepository.countProfilePosts(owner.getId()),
                territory.getPurchasedAt(),
                territory.getLeaseStartedAt(),
                territory.getExpiresAt(),
                daysRemaining,
                expired,
                territory.getRenewalCount(),
                profile != null && profile.getProfileType() != null ? profile.getProfileType().name() : "STANDARD",
                canOpen && profile != null ? profile.getWebsite() : null,
                socialLinks,
                engagement.likeCount(),
                engagement.saveCount(),
                engagement.liked(),
                engagement.saved(),
                viewerId != null && !reserved && viewerId.equals(owner.getId()),
                canOpen,
                lockedReason,
                stats(territory, viewsToday, daysRemaining, expired),
                /* ⚠️ Kapak `canOpen` kapısının ARKASINDA (V53): kilitli kartta
                   metin gizlenip fotoğraf gösterilirse kilit yarım kalır. */
                canOpen && !reserved ? MediaUrls.of(territory.getStoreCoverMediaId()) : null,
                /* Kategori rozetinde kişisel bir bilgi yok; kilitli kartta da
                   çizilir — "burada ne var" sorusunun cevabı kilidin konusu değil. */
                territory.getCategoryId() != null
                        ? storeCategoryRepository.findById(territory.getCategoryId())
                                .map(com.waydee.territory.api.dto.StoreCategoryDtos.StoreCategoryResponse::from)
                                .orElse(null)
                        : null);
    }

    // ------------------------------------------------------------ parçalar

    private List<CardChip> chips(Territory territory, long viewsToday, long daysRemaining,
                                 boolean expired, TerritoryProfile profile) {
        List<CardChip> chips = new ArrayList<>();
        if (expired) {
            chips.add(new CardChip("EXPIRED", "Süresi doldu", "danger"));
        } else {
            chips.add(new CardChip("ACTIVE", "Aktif", "success"));
        }
        if (!expired && viewsToday >= TREND_VIEWS_TODAY) {
            chips.add(new CardChip("TREND", "Trend", "warning"));
        }
        if (territory.isVerified()) {
            chips.add(new CardChip("VERIFIED", "Doğrulanmış Alan", "info"));
        }
        if (territory.isReserved()) {
            chips.add(new CardChip("RESERVED", "Kurum alanı", "violet"));
        }
        if (!expired && daysRemaining <= EXPIRING_SOON_DAYS) {
            chips.add(new CardChip("EXPIRING", daysRemaining + " gün kaldı", "warning"));
        }
        if (profile != null && profile.isLiveActive() && profile.getLiveUrl() != null) {
            chips.add(new CardChip("LIVE", "Canlı Yayın", "danger"));
        }
        return chips;
    }

    /**
     * Karttaki 4'lü ızgara. Sunucuda üretilir ki metinler (birim, kısaltma,
     * "gün kaldı") kullanıcı ve yönetim tarafında birebir aynı okunsun.
     */
    private List<CardStat> stats(Territory territory, long viewsToday, long daysRemaining, boolean expired) {
        long m2 = areaM2(territory.getAreaKm2());
        return List.of(
                new CardStat("AREA", "Alan Büyüklüğü", formatArea(m2), territory.getRadiusM() + " m yarıçap", "mint"),
                new CardStat("VALUE", "Alan Değeri",
                        formatMoney(territory.getPricePaid(), territory.getCurrency()),
                        "Yıllık kira bedeli", "peach"),
                new CardStat("LEASE", expired ? "Süresi doldu" : "Kalan süre",
                        expired ? "Yenile" : daysRemaining + " gün",
                        "Bitiş: " + formatDate(territory.getExpiresAt()), expired ? "peach" : "mint"),
                new CardStat("VIEWS", "Bugün Görüntülenme", formatCount(viewsToday), "Toplam izlenim", "lilac"));
    }

    private static long areaM2(BigDecimal areaKm2) {
        return areaKm2.multiply(BigDecimal.valueOf(1_000_000)).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** 46.800 m² · 12,4 km² — küçük alanlar m², büyükler km² okunur. */
    private static String formatArea(long m2) {
        if (m2 < 1_000_000) {
            return group(m2) + " m²";
        }
        BigDecimal km2 = BigDecimal.valueOf(m2).divide(BigDecimal.valueOf(1_000_000), 2, RoundingMode.HALF_UP);
        return km2.toPlainString().replace('.', ',') + " km²";
    }

    private static String formatMoney(BigDecimal amount, String currency) {
        String symbol = switch (currency == null ? "TRY" : currency) {
            case "TRY" -> "₺";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> currency + " ";
        };
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        String[] parts = v.toPlainString().split("\\.");
        return symbol + group(Long.parseLong(parts[0])) + "," + parts[1];
    }

    /** 3K+ / 1,2M gibi kısa sayaç — kartta yer dar. */
    private static String formatCount(long n) {
        if (n < 1000) {
            return String.valueOf(n);
        }
        if (n < 1_000_000) {
            long k = n / 1000;
            return k + "K+";
        }
        BigDecimal m = BigDecimal.valueOf(n).divide(BigDecimal.valueOf(1_000_000), 1, RoundingMode.DOWN);
        return m.toPlainString().replace('.', ',') + "M";
    }

    private static String group(long n) {
        return String.format("%,d", n).replace(',', '.');
    }

    private static final String[] MONTHS = {
            "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    };

    private static String formatDate(Instant instant) {
        var d = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
    }
}
