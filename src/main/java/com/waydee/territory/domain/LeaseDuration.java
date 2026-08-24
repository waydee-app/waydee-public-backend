package com.waydee.territory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * Kiralama süreleri ve fiyat çarpanları.
 *
 * <p><b>Model:</b> çözülen km² fiyatı <b>bir yıllık</b> bedeldir (mevcut tüm
 * fiyat bölgeleri böyle tanımlanmıştı; anlamı değiştirmek eski verinin
 * fiyatını sessizce bozardı). Kısa süreler bu yıllık bedelin bir <b>oranıdır</b>
 * ve gün başına daha pahalıdır.
 *
 * <p>⚠️ <b>Amaç uzun süreli satış.</b> Çarpanlar bilinçli olarak doğrusal
 * değil: 1 günün günlük maliyeti, 365 günün günlük maliyetinin <b>2,5 katı</b>.
 * Böylece "bir gün deneyeyim" mümkün kalırken, uzun süre almak açıkça
 * kazançlı görünür.
 *
 * <p>Türetme (365 günlük bedel = 1,0 kabul edilerek):
 * <pre>
 *   çarpan(gün) = (gün / 365) × günlükPrimi(gün)
 * </pre>
 * Günlük primi 1 günde 2,50 → 365 günde 1,00'e iner. Aşağıdaki değerler
 * referans tablodan (bkz. vault 09) birebir alınmıştır; indirim yüzdeleri
 * o tabloyla eşleşir: 7g %15 · 30g %35 · 90g %39,5 · 180g %46,2 · 365g %60.
 */
public enum LeaseDuration {

    D1(1, "0.006849"),
    D7(7, "0.040753"),
    D14(14, "0.075660"),
    D30(30, "0.133562"),
    D90(90, "0.372945"),
    D180(180, "0.663288"),
    D278(278, "0.885337"),
    D365(365, "1.000000");

    /** Gün sayısı — kira süresi buradan hesaplanır. */
    private final int days;
    /** Yıllık bedele oran. */
    private final BigDecimal multiplier;

    LeaseDuration(int days, String multiplier) {
        this.days = days;
        this.multiplier = new BigDecimal(multiplier);
    }

    public int days() {
        return days;
    }

    public BigDecimal multiplier() {
        return multiplier;
    }

    /** Varsayılan: bir yıl (eski davranışla aynı). */
    public static LeaseDuration defaultDuration() {
        return D365;
    }

    /**
     * Gün sayısından süre bulur.
     *
     * <p>⚠️ Serbest gün kabul edilmez: istemciden gelen rastgele bir sayı,
     * fiyat çarpanı tablosunun dışına düşer ve "1000 gün al, çarpan yok"
     * gibi bir boşluk yaratırdı. Tanımsız değer varsayılana düşer.
     */
    public static LeaseDuration ofDays(Integer days) {
        if (days == null) {
            return defaultDuration();
        }
        return Arrays.stream(values())
                .filter(d -> d.days == days)
                .findFirst()
                .orElse(defaultDuration());
    }

    public static List<LeaseDuration> all() {
        return List.of(values());
    }

    /**
     * Bu süre için ödenecek bedel.
     *
     * @param annualPrice bir yıllık bedel (alan × km² fiyatı)
     */
    public BigDecimal price(BigDecimal annualPrice) {
        return annualPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Listedeki en ucuz günlük birim fiyata göre indirim yüzdesi.
     *
     * <p>Referans <b>1 günlük</b> orandır: kullanıcı "bir gün alsaydım ne
     * öderdim"e göre ne kazandığını görür — tablodaki yüzdeler de böyle.
     */
    public BigDecimal discountPercent() {
        BigDecimal dailyOfOneDay = D1.multiplier; // 1 günün günlük oranı
        BigDecimal dailyOfThis = multiplier.divide(BigDecimal.valueOf(days), 10, RoundingMode.HALF_UP);
        return BigDecimal.ONE.subtract(dailyOfThis.divide(dailyOfOneDay, 10, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
