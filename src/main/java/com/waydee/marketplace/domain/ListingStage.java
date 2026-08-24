package com.waydee.marketplace.domain;

/** Girişimin olgunluk aşaması (startup vitrinleri için). */
public enum ListingStage {
    IDEA("Fikir"),
    MVP("İlk ürün"),
    EARLY_REVENUE("İlk gelir"),
    GROWTH("Büyüme"),
    ESTABLISHED("Yerleşik");

    private final String label;

    ListingStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
