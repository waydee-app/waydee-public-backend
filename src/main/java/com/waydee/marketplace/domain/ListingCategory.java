package com.waydee.marketplace.domain;

/** Stant kategorisi — kart rengini ve filtreleri belirler. */
public enum ListingCategory {
    STARTUP("Girişim"),
    ECOMMERCE("E-ticaret"),
    FOOD("Yeme-içme"),
    ART("Sanat & tasarım"),
    SERVICE("Hizmet"),
    TECH("Teknoloji"),
    EDUCATION("Eğitim"),
    HEALTH("Sağlık"),
    TRAVEL("Seyahat"),
    OTHER("Diğer");

    private final String label;

    ListingCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
