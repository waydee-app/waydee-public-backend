package com.waydee.identity.domain;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Üyeliğin <b>faturalama dönemi</b> (V37).
 *
 * <p>🔴 <b>Fiyat her zaman AYLIK eşdeğerdir</b>, yıllık toplam değil. Yani
 * PRO yıllıkta {@code 10,00} saklanır; kullanıcı "10 $/ay, yıllık faturalanır"
 * görür ve kartından {@code 10 × 12 = 120 $} çekilir. Toplamı saklamak, ekranda
 * her seferinde 12'ye bölmeyi gerektirir ve indirim oranını ({@code 13 → 10})
 * hesaplanamaz hâle getirirdi.
 *
 * <p>⚠️ Süre <b>ay/yıl</b> değil <b>gün</b> cinsindendir: takvim ayı 28-31 gün
 * arasında oynar, üyelik süresi ise ödenen bedelin karşılığı olarak sabit
 * olmalı. Aynı sabit V35'ten beri {@code 30 gün} olarak kullanılıyor.
 */
public enum BillingPeriod {

    /** Aylık: 30 gün, tahsilat = aylık fiyat × 1. */
    MONTHLY(Duration.ofDays(30), 1),

    /** Yıllık: 365 gün, tahsilat = aylık eşdeğer × 12. */
    YEARLY(Duration.ofDays(365), 12);

    private final Duration length;
    private final int installments;

    BillingPeriod(Duration length, int installments) {
        this.length = length;
        this.installments = installments;
    }

    /** Bir ödemenin uzattığı süre. */
    public Duration length() {
        return length;
    }

    /** Aylık eşdeğer fiyattan <b>tahsil edilecek</b> tutarı üretir. */
    public BigDecimal chargeFor(BigDecimal monthlyEquivalent) {
        return monthlyEquivalent.multiply(BigDecimal.valueOf(installments));
    }

    public boolean yearly() {
        return this == YEARLY;
    }

    /** Bilinmeyen/boş değer aylığa düşer — bozuk veri uzun üyelik vermemeli. */
    public static BillingPeriod ofNullable(String raw) {
        if (raw == null) {
            return MONTHLY;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MONTHLY;
        }
    }
}
