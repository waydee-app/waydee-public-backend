package com.waydee.marketplace.domain;

public enum MarketplaceStatus {
    /** Hazırlanıyor — yalnız admin görür, haritada çıkmaz. */
    DRAFT,
    /** Yayında ve başvuruya açık. */
    OPEN,
    /** Yayında ama yeni başvuru alınmıyor (stantlar görünmeye devam eder). */
    CLOSED,
    /** Arşiv — haritadan ve listelerden düşer, veri durur. */
    ARCHIVED
}
