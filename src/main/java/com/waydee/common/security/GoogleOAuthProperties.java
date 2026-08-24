package com.waydee.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Google ile giriş yapılandırması.
 *
 * <p>⚠️ {@code clientSecret} <b>koda yazılmaz</b> — kök {@code .env} / Dokploy
 * Environment üzerinden gelir ({@code GOOGLE_CLIENT_SECRET}). {@code clientId}
 * gizli değildir (tarayıcıya da servis edilebilir) ama yine de ortamdan gelir
 * ki geliştirme ve üretim farklı OAuth istemcileri kullanabilsin.
 *
 * @param enabled       kapalıyken uçlar 503 döner ve arayüz düğmeyi hiç çizmez
 * @param clientId      Google Cloud Console → OAuth 2.0 İstemci Kimliği
 * @param clientSecret  aynı istemcinin gizli anahtarı (sunucu tarafı takas için)
 * @param redirectUri   Google'a bildirilen dönüş adresi — Console'daki
 *                      "Yetkili yönlendirme URI'leri" ile <b>birebir</b> aynı olmalı
 * @param appBaseUrl    kullanıcı arayüzünün kökü; akış buraya geri döner
 * @param stateTtl      yetkilendirme isteğinin geçerlilik süresi (CSRF penceresi)
 * @param ticketTtl     dönüşte üretilen tek kullanımlık takas biletinin ömrü
 */
@ConfigurationProperties(prefix = "waydee.oauth.google")
public record GoogleOAuthProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String redirectUri,
        String appBaseUrl,
        Duration stateTtl,
        Duration ticketTtl
) {

    /** Yapılandırma eksikse özellik "açık" sayılmaz — yarım kurulum çalışan bir düğme değildir. */
    public boolean isConfigured() {
        return enabled
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }
}
