package com.waydee.social.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Etiket istatistikleri sözleşmesi. */
public final class TagStatsDtos {

    private TagStatsDtos() {
    }

    /**
     * Gösterim bildirimi — bir gönderinin ekranda çizilen etiketleri.
     *
     * <p>⚠️ Toplu: etiket başına ayrı istek, beş etiketli bir gönderide her
     * açılışta beş HTTP isteği demekti.
     */
    public record ImpressionRequest(
            @NotNull @Size(min = 1, max = 50, message = "En fazla 50 etiket bildirilebilir")
            List<@NotNull UUID> tagIds
    ) {
    }

    /** Tek bir etiketin özeti. */
    public record TagRow(
            UUID tagId,
            String productName,
            String productUrl,
            BigDecimal price,
            String currency,
            int impressions,
            int clicks,
            /** Tıklama oranı, yüzde (iki ondalık). */
            double ctr
    ) {
    }

    /** Grafiğin tek çubuğu. */
    public record TagDaily(LocalDate day, int impressions, int clicks) {
    }

    public record TagStatsView(
            UUID postId,
            int days,
            int totalImpressions,
            int totalClicks,
            double ctr,
            List<TagRow> tags,
            List<TagDaily> daily
    ) {
    }
}
