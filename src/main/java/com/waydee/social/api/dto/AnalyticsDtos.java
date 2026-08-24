package com.waydee.social.api.dto;

import com.waydee.identity.api.dto.FollowDtos.UserSummary;

import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    /** Gün bazında görüntülenme (grafik için; son 14 gün, boş günler 0). */
    public record DayCount(String date, long count) {
    }

    public record ViewerRow(UserSummary viewer, Instant viewedAt) {
    }

    public record AnalyticsResponse(
            long totalViews,
            long uniqueViewers,
            List<DayCount> viewsByDay,
            List<ViewerRow> recentViewers
    ) {
    }

    // ------------------------------------------------------- zengin rapor (28 Tem 2026)

    /** Bölge bazında görüntülenme kırılımı. */
    public record TerritoryStat(
            java.util.UUID territoryId,
            String name,
            String regionLabel,
            java.math.BigDecimal areaKm2,
            long views,
            long uniqueViewers
    ) {
    }

    /** En çok görüntüleyen kişi. */
    public record TopViewer(UserSummary viewer, long views, Instant lastViewedAt) {
    }

    /** Etiketli sayaç — saat/gün dağılımı gibi basit dağılımlar için. */
    public record LabelCount(String label, long count) {
    }

    /**
     * Kullanıcının tam raporu.
     *
     * @param days              rapor dönemi (gün)
     * @param viewsInPeriod     dönemdeki görüntülenme
     * @param viewsPrevPeriod   bir önceki eşit dönemdeki görüntülenme (trend oku için)
     * @param changePercent     yüzde değişim; önceki dönem 0 ise null (oran tanımsız)
     * @param busiestHour       en yoğun saat (0-23); veri yoksa null
     */
    public record ReportResponse(
            int days,
            long totalViews,
            long uniqueViewers,
            long viewsInPeriod,
            long viewsPrevPeriod,
            Double changePercent,
            List<DayCount> viewsByDay,
            List<LabelCount> viewsByWeekday,
            Integer busiestHour,

            long territoryCount,
            java.math.BigDecimal totalAreaKm2,
            java.math.BigDecimal portfolioValue,
            String currency,
            List<TerritoryStat> territories,

            long followers,
            long following,
            long pendingRequests,

            long postCount,
            long postsInPeriod,
            long likesReceived,
            long commentsReceived,
            long activeStories,

            // --- Analytics ekranının üst kartları (5 Ağu 2026) ---
            /** Fotoğraflara bırakılmış toplam ürün etiketi. */
            long tagCount,
            /** Gönderilerimin toplam kaydedilme (bookmark) sayısı. */
            long saveCount,
            long collectionCount,
            long linkCount,

            List<TopViewer> topViewers,
            List<ViewerRow> recentViewers
    ) {
    }
}
