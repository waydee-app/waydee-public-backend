package com.waydee.billing.domain;

/**
 * Fatura durumu.
 *
 * Şu an ödeme mock olduğu için satın alma anında kesilen fatura doğrudan
 * {@link #PAID} olur. Gerçek ödeme altyapısı geldiğinde akış
 * {@code DRAFT → ISSUED → PAID} (ya da {@link #CANCELLED}) olarak işletilir.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    CANCELLED
}
