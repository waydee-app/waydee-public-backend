package com.waydee.social.api.dto;

import java.math.BigDecimal;
import java.util.List;

public final class TrendingDtos {

    private TrendingDtos() {
    }

    /**
     * Duyuru şeridinin tek öğesi.
     *
     * @param climb sıra kaç basamak yükseldi (negatif = düştü, null = listeye yeni girdi)
     * @param reason "neden trend" — en güçlü sinyalden üretilmiş tek cümle
     */
    @SuppressWarnings("java:S107")
    public record TrendingItem(
            /** TERRITORY | USER */
            String type,
            String id,
            /**
             * 🔴 17 Ağu 2026 — profil adı. İstemci {@code /u/{id}} köprüsüne
             * uğramadan doğrudan {@code /{username}}'e gitsin diye.
             * ⚠️ Yalnız USER için dolu.
             */
            String username,
            String title,
            String subtitle,
            String avatarUrl,
            int rank,
            Integer climb,
            BigDecimal score,
            int views7d,
            int likes7d,
            int saves7d,
            int posts7d,
            int followers7d,
            String reason,
            /** Yalnız TERRITORY için dolu — haritada o noktaya uçmak için. */
            Double lng,
            Double lat
    ) {
    }

    public record TrendingResponse(List<TrendingItem> territories, List<TrendingItem> users) {
    }
}
