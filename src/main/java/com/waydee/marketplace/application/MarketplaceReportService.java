package com.waydee.marketplace.application;

import com.waydee.marketplace.api.dto.MarketplaceReportDtos.KindBreakdown;
import com.waydee.marketplace.api.dto.MarketplaceReportDtos.MarketplaceRow;
import com.waydee.marketplace.api.dto.MarketplaceReportDtos.ReportResponse;
import com.waydee.marketplace.api.dto.MarketplaceReportDtos.TopListing;
import com.waydee.marketplace.domain.ListingStatus;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceListing;
import com.waydee.marketplace.domain.MarketplaceStatus;
import com.waydee.marketplace.infrastructure.MarketplaceListingRepository;
import com.waydee.marketplace.infrastructure.MarketplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pazar yeri raporları (yönetim).
 *
 * Sorular: hangi pazar çalışıyor, nerede tıkanma var, onay hızı ne, hangi
 * stantlar öne çıkıyor. Hepsi <b>toplu sorgularla</b> hesaplanır — pazar
 * başına ek sorgu yoktur.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceReportService {

    private final MarketplaceRepository marketplaceRepository;
    private final MarketplaceListingRepository listingRepository;

    @Transactional(readOnly = true)
    public ReportResponse report(int days) {
        int window = switch (days) {
            case 7, 30, 90, 365 -> days;
            default -> 30;
        };
        Instant since = Instant.now().minus(window, ChronoUnit.DAYS);

        List<Marketplace> markets = marketplaceRepository.findAll();
        List<MarketplaceListing> all = listingRepository.findAll();

        // ---- genel sayaçlar
        long totalMarkets = markets.stream().filter(m -> m.getStatus() != MarketplaceStatus.ARCHIVED).count();
        long openMarkets = markets.stream().filter(m -> m.getStatus() == MarketplaceStatus.OPEN).count();
        long pending = all.stream().filter(l -> l.getStatus() == ListingStatus.PENDING).count();
        long approved = all.stream().filter(l -> l.getStatus() == ListingStatus.APPROVED).count();
        long rejected = all.stream().filter(l -> l.getStatus() == ListingStatus.REJECTED).count();
        long inWindow = all.stream().filter(l -> l.getSubmittedAt().isAfter(since)).count();

        // ---- onay oranı ve ortalama karar süresi
        List<MarketplaceListing> decided = all.stream()
                .filter(l -> l.getReviewedAt() != null && l.getReviewedBy() != null)
                .toList();
        double approvalRate = decided.isEmpty() ? 0
                : (double) decided.stream().filter(l -> l.getStatus() == ListingStatus.APPROVED).count()
                        / decided.size() * 100;
        double avgHours = decided.isEmpty() ? 0
                : decided.stream()
                        .mapToLong(l -> java.time.Duration.between(l.getSubmittedAt(), l.getReviewedAt()).toMinutes())
                        .average().orElse(0) / 60.0;

        // ---- bekleyenlerin en eskisi (tıkanma göstergesi)
        Long oldestPendingHours = all.stream()
                .filter(l -> l.getStatus() == ListingStatus.PENDING)
                .map(MarketplaceListing::getSubmittedAt)
                .min(Comparator.naturalOrder())
                .map(t -> java.time.Duration.between(t, Instant.now()).toHours())
                .orElse(null);

        // ---- pazar bazında kırılım
        Map<UUID, List<MarketplaceListing>> byMarket = new LinkedHashMap<>();
        all.forEach(l -> byMarket.computeIfAbsent(l.getMarketplaceId(), k -> new ArrayList<>()).add(l));

        List<MarketplaceRow> rows = markets.stream()
                .filter(m -> m.getStatus() != MarketplaceStatus.ARCHIVED)
                .map(m -> {
                    List<MarketplaceListing> ls = byMarket.getOrDefault(m.getId(), List.of());
                    long p = ls.stream().filter(l -> l.getStatus() == ListingStatus.PENDING).count();
                    long a = ls.stream().filter(l -> l.getStatus() == ListingStatus.APPROVED).count();
                    long r = ls.stream().filter(l -> l.getStatus() == ListingStatus.REJECTED).count();
                    long views = ls.stream().mapToLong(MarketplaceListing::getViewCount).sum();
                    long likes = ls.stream().mapToLong(MarketplaceListing::getLikeCount).sum();
                    // Doluluk: kontenjan yoksa null (yüzde anlamsız).
                    Integer fill = m.getMaxListings() == null ? null
                            : (int) Math.round(a * 100.0 / m.getMaxListings());
                    return new MarketplaceRow(
                            m.getId(), m.getName(), m.getSlug(), m.getKind().name(), m.getKind().label(),
                            m.getStatus().name(), m.getAccentColor(),
                            (int) p, (int) a, (int) r, m.getMaxListings(), fill, views, likes);
                })
                .sorted(Comparator.comparingInt(MarketplaceRow::pending).reversed())
                .toList();

        // ---- tür kırılımı
        Map<String, KindBreakdown> kinds = new LinkedHashMap<>();
        for (Marketplace m : markets) {
            if (m.getStatus() == MarketplaceStatus.ARCHIVED) {
                continue;
            }
            List<MarketplaceListing> ls = byMarket.getOrDefault(m.getId(), List.of());
            long a = ls.stream().filter(l -> l.getStatus() == ListingStatus.APPROVED).count();
            kinds.merge(m.getKind().name(),
                    new KindBreakdown(m.getKind().name(), m.getKind().label(), 1, (int) a),
                    (x, y) -> new KindBreakdown(x.kind(), x.label(), x.markets() + 1, x.listings() + y.listings()));
        }

        // ---- en çok ilgi gören stantlar
        List<TopListing> top = all.stream()
                .filter(l -> l.getStatus() == ListingStatus.APPROVED)
                .sorted(Comparator.comparingInt((MarketplaceListing l) -> l.getLikeCount() * 3 + l.getViewCount())
                        .reversed())
                .limit(10)
                .map(l -> new TopListing(l.getId(), l.getTitle(), l.getOwner().getUsername(),
                        l.getViewCount(), l.getLikeCount(), l.isFeatured()))
                .toList();

        return new ReportResponse(
                window, (int) totalMarkets, (int) openMarkets,
                (int) pending, (int) approved, (int) rejected, (int) inWindow,
                Math.round(approvalRate * 10) / 10.0,
                Math.round(avgHours * 10) / 10.0,
                oldestPendingHours,
                rows, List.copyOf(kinds.values()), top);
    }
}
