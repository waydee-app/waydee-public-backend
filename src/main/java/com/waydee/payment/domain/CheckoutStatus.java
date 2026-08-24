package com.waydee.payment.domain;

/**
 * Bir ödeme oturumunun yaşam döngüsü.
 *
 * <p>Yalnız {@link #PENDING} alanı <b>rezerve tutar</b> — çakışma kontrolü
 * sadece bu durumdaki kayıtlara bakar.
 */
public enum CheckoutStatus {
    /** Ödeme sayfası açıldı, sonuç bekleniyor. Daire bu süre boyunca rezervedir. */
    PENDING,
    /** Sağlayıcı ödemeyi onayladı ve bölge oluşturuldu. */
    PAID,
    /** Süre doldu, ödeme gelmedi. Alan yeniden serbest. */
    EXPIRED,
    /** Kullanıcı vazgeçti. */
    CANCELLED,
    /** Sağlayıcı ödemeyi reddetti ya da tamamlama sırasında hata oluştu. */
    FAILED
}
