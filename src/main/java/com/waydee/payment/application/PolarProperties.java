package com.waydee.payment.application;

import com.waydee.identity.domain.BillingPeriod;
import com.waydee.identity.domain.UserPlan;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Locale;

/**
 * Polar ayarları (12 Ağustos 2026 — LemonSqueezy'nin yerine).
 *
 * <p>🔴 <b>Neden dört ürün kimliği:</b> LemonSqueezy'de tek bir varyant açıp
 * her ödemede {@code custom_price} ile fiyatı ezmek mümkündü. <b>Polar'da
 * abonelik ürününün fiyatı ezilemez</b> — tutar ürüne bağlıdır. Bu yüzden
 * {plan} × {dönem} dört hücrenin her biri Polar panelinde <b>ayrı bir ürün</b>
 * olarak açılır ve kimlikleri buraya yazılır.
 *
 * <p>⚠️ <b>Fiyat artık iki yerde:</b> Polar ürünü (gerçek tahsilat) ve
 * {@code app_settings} (arayüzde gösterilen). İkisi ayrışırsa kullanıcı ekranda
 * bir tutar görüp başka bir tutar öder. Polar'ın yıllık ürününe <b>yıllık
 * toplam</b> girilir (aylık eşdeğer × 12), çünkü bizde saklanan değer aylık
 * eşdeğerdir (bkz. {@link BillingPeriod}).
 *
 * @param apiKey        organizasyon erişim jetonu ({@code polar_oat_…}). Koda yazılmaz.
 * @param server        {@code production} ya da {@code sandbox} — taban adresi belirler
 * @param webhookSecret Standard Webhooks imzasının doğrulandığı gizli anahtar
 * @param timeout       API çağrısının üst sınırı
 */
@ConfigurationProperties(prefix = "waydee.payment.polar")
public record PolarProperties(
        String apiKey,
        String server,
        String webhookSecret,
        String productProMonthly,
        String productProYearly,
        String productPremiumMonthly,
        String productPremiumYearly,
        Duration timeout
) {
    private static final String PRODUCTION = "https://api.polar.sh";
    private static final String SANDBOX = "https://sandbox-api.polar.sh";

    public PolarProperties {
        if (timeout == null || timeout.isZero()) {
            timeout = Duration.ofSeconds(15);
        }
        server = server == null || server.isBlank()
                ? "production"
                : server.trim().toLowerCase(Locale.ROOT);
    }

    /** Sandbox tamamen ayrı bir hesaptır: jeton da ürün kimlikleri de farklıdır. */
    public boolean sandbox() {
        return "sandbox".equals(server);
    }

    public String baseUrl() {
        return sandbox() ? SANDBOX : PRODUCTION;
    }

    /**
     * Bu plan + dönem için Polar ürün kimliği.
     *
     * <p>⚠️ Eşleme burada, tek yerde durur. Ürün kimliğini çağrı yerinde seçmek,
     * dört hücreden birinin sessizce yanlış ürüne bağlanması demekti — kullanıcı
     * Premium alıp Pro fiyatı öderdi.
     */
    public String productId(UserPlan plan, BillingPeriod period) {
        boolean yearly = period != null && period.yearly();
        if (plan == UserPlan.PREMIUM) {
            return yearly ? productPremiumYearly : productPremiumMonthly;
        }
        return yearly ? productProYearly : productProMonthly;
    }

    /**
     * Gerçek tahsilat için yeterli yapılandırma var mı?
     *
     * <p>Dört ürünün <b>hepsi</b> aranır: üçü tanımlıyken dördüncüsü eksik
     * kalırsa o hücreyi satın almak isteyen kullanıcı ödeme sayfası yerine hata
     * görürdü. Eksikliğin açılışta görülmesi, satın alma anında görülmesinden
     * iyidir ({@code ProductionSecretsGuard}).
     */
    public boolean isConfigured() {
        return notBlank(apiKey)
                && notBlank(productProMonthly) && notBlank(productProYearly)
                && notBlank(productPremiumMonthly) && notBlank(productPremiumYearly);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
