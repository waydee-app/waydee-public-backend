package com.waydee.monetization.domain;

/** Gelir başvurusunun durumu. Sıra bilinçli: açık durumlar önce. */
public enum MonetizationStatus {
    /** Gönderildi, henüz kimse bakmadı. */
    PENDING,
    /** Yönetici incelemeye aldı. */
    REVIEWING,
    /** Kabul edildi. */
    APPROVED,
    /** Reddedildi — `decisionNote` gerekçeyi taşır. */
    REJECTED
}
