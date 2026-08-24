package com.waydee.marketplace.domain;

public enum ListingStatus {
    /** Üye hazırlıyor, henüz göndermedi. */
    DRAFT,
    /** Gönderildi, yönetim onayı bekliyor. */
    PENDING,
    /** Onaylandı — haritada stant olarak görünür. */
    APPROVED,
    /** Reddedildi; gerekçe {@code reviewNote}'ta. Üye düzenleyip tekrar gönderebilir. */
    REJECTED,
    /** Üye kendi başvurusunu geri çekti. */
    WITHDRAWN
}
