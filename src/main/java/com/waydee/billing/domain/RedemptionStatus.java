package com.waydee.billing.domain;

/**
 * Kupon kullanımının yaşam döngüsü.
 *
 * <p>⚠️ Ödeme asenkron olduğu için kupon "oturum açılırken" kesin tüketilemez:
 * vazgeçen her kullanıcı kontenjanı yakardı. Hiç tüketmemek de olmaz: tek
 * kullanımlık kuponu aynı anda onlarca kişi kullanırdı. Bu yüzden üç durum var.
 */
public enum RedemptionStatus {
    /** Oturum açıldı, kontenjandan düşüldü, ödeme bekleniyor. */
    RESERVED,
    /** Ödeme geldi — kullanım kesinleşti. */
    CONFIRMED,
    /** Oturum süresi doldu ya da iptal edildi — kontenjan GERİ VERİLDİ. */
    RELEASED
}
