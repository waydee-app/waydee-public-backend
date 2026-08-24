package com.waydee.payment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Ödeme ayarları.
 *
 * @param provider          aktif sağlayıcı: {@code mock} (yerel geliştirme) ya da {@code polar}
 * @param reservationTtl    ödeme sayfası açıkken dairenin rezerve kalacağı süre.
 *                          Çok kısa olursa kullanıcı kart bilgisini girerken alanı
 *                          kaybeder; çok uzun olursa vazgeçen biri alanı gereksiz
 *                          yere kilitler.
 * @param returnUrl         ödemeden sonra kullanıcının döneceği arayüz adresi
 * @param allowMock         <b>üretimde</b> sahte ödemeye bilinçli izin.
 *                          <p>Varsayılan {@code false}: {@code prod} profilinde
 *                          {@code provider=mock} ile açılış engellenir — sahte geçit
 *                          bedava bölge dağıtmak demektir. Ancak canlı ortamda
 *                          <b>uçtan uca test</b> yapmak (sağlayıcı hesabı henüz
 *                          etkinleşmemişken bile bölge alıp akışı denemek) gerçek
 *                          bir ihtiyaç. Bu bayrak açıkken sahte ekran üretimde de
 *                          çalışır; karar <b>görünür ve kasıtlı</b> olsun diye
 *                          ayrı bir anahtar — sessizce {@code PAYMENT_PROVIDER=mock}
 *                          yazmakla aynı şey değildir (açılışta uyarı loglanır).
 */
@ConfigurationProperties(prefix = "waydee.payment")
public record PaymentProperties(
        String provider,
        Duration reservationTtl,
        /**
         * PRO üyelik ücreti. Tanıtım sayfasındaki vaat <b>$10/ay</b>; değer
         * yapılandırmadan gelir ki fiyat değişince kod değişmesin.
         */
        java.math.BigDecimal proPrice,
        String proCurrency,
        String returnUrl,
        boolean allowMock
) {
    public PaymentProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        provider = provider.toLowerCase(java.util.Locale.ROOT).trim();
        if (proPrice == null || proPrice.signum() <= 0) {
            proPrice = new java.math.BigDecimal("10.00");
        }
        if (proCurrency == null || proCurrency.isBlank()) {
            proCurrency = "USD";
        }
        if (reservationTtl == null || reservationTtl.isZero()) {
            reservationTtl = Duration.ofMinutes(30);
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "http://localhost:5175/payment/return";
        }
    }

    /**
     * Gerçek tahsilat açık mı?
     *
     * <p>⚠️ Eski {@code lemonsqueezy} değeri de kabul edilir ama <b>Polar'a
     * yönlendirir</b>: LemonSqueezy tamamen kaldırıldı (12 Ağu 2026) ve
     * üretimdeki Dokploy ortamında o dize hâlâ yazılı olabilir. Bilinmeyen bir
     * değeri sessizce sahte sağlayıcıya düşürmek, gerçek tahsilat beklenirken
     * bedava üyelik dağıtmak olurdu.
     */
    public boolean isPolar() {
        return "polar".equals(provider) || "lemonsqueezy".equals(provider);
    }
}
