package com.waydee.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cloudflare Turnstile (bot koruması) ayarları.
 *
 * <p>Gizli anahtar <b>asla</b> kaynak koda yazılmaz: {@code TURNSTILE_SECRET}
 * ortam değişkeninden gelir (kök {@code .env} dosyası Spring'e
 * {@code spring.config.import} ile aktarılır). {@code enabled=false} iken
 * doğrulama tamamen atlanır — offline geliştirme bu sayede mümkündür.
 *
 * @param enabled    doğrulama açık mı
 * @param secretKey  Cloudflare gizli anahtarı (site key istemcide, bu sunucuda)
 * @param verifyUrl  siteverify uç adresi
 * @param timeout    Cloudflare'e giden çağrının üst sınırı
 */
@ConfigurationProperties(prefix = "waydee.security.turnstile")
public record TurnstileProperties(
        boolean enabled,
        String secretKey,
        String verifyUrl,
        Duration timeout
) {
    public TurnstileProperties {
        if (verifyUrl == null || verifyUrl.isBlank()) {
            verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(5);
        }
    }
}
