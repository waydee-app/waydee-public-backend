package com.waydee.moderation.domain;

public enum ReportStatus {
    /** Yeni gelen şikayet. */
    OPEN,
    /** Admin inceliyor. */
    REVIEWING,
    /** Haklı bulundu ve işlem yapıldı. */
    RESOLVED,
    /** Haksız bulundu. */
    REJECTED
}
