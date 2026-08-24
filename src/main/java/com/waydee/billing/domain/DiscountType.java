package com.waydee.billing.domain;

/**
 * Kupon turu.
 *
 * <p>KIRMIZI: V40'tan beri asil tur <b>PLAN</b>'dir - kupon bir indirim degil,
 * bir <b>paket hediyesi</b>. Indirim turleri (PERCENT/FIXED) uygulanacaklari
 * odeme kalmadigi icin (bolge odemesi V38'de kaldirildi) yalniz GECMIS
 * kayitlarin okunabilmesi icin duruyor; yeni kupon uretilmez.
 */
public enum DiscountType {
    /** Yuzde indirim; `maxDiscount` ile tavanlanabilir. (Devre disi) */
    PERCENT,
    /** Sabit tutar indirimi; para birimi eslesmezse uygulanmaz. (Devre disi) */
    FIXED,
    /** <b>Paket hediyesi</b>: kod girilince plan aninda yukselir. */
    PLAN;

    public boolean isPlanGift() {
        return this == PLAN;
    }
}
