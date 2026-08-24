package com.waydee.marketplace.api.dto;

import java.util.List;
import java.util.UUID;

public final class MarketplaceReportDtos {

    private MarketplaceReportDtos() {
    }

    /** @param fillPercent kontenjan yoksa null — yüzde anlamsız olurdu. */
    @SuppressWarnings("java:S107")
    public record MarketplaceRow(
            UUID id, String name, String slug, String kind, String kindLabel,
            String status, String accentColor,
            int pending, int approved, int rejected,
            Integer maxListings, Integer fillPercent,
            long views, long likes
    ) {
    }

    public record KindBreakdown(String kind, String label, int markets, int listings) {
    }

    public record TopListing(UUID id, String title, String ownerUsername,
                             int views, int likes, boolean featured) {
    }

    /**
     * @param oldestPendingHours en eski bekleyen başvurunun yaşı — tıkanma göstergesi (yoksa null)
     */
    @SuppressWarnings("java:S107")
    public record ReportResponse(
            int days,
            int totalMarkets,
            int openMarkets,
            int pendingListings,
            int approvedListings,
            int rejectedListings,
            int submittedInWindow,
            double approvalRate,
            double avgDecisionHours,
            Long oldestPendingHours,
            List<MarketplaceRow> marketplaces,
            List<KindBreakdown> kinds,
            List<TopListing> topListings
    ) {
    }
}
