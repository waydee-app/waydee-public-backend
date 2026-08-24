package com.waydee.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Giden e-posta ayarları (hesap doğrulama, şifre sıfırlama, e-posta değişimi).
 *
 * <p>SMTP bağlantı bilgileri Spring'in kendi {@code spring.mail.*} anahtarlarından
 * gelir; burada yalnızca <b>uygulamaya özel</b> davranış tutulur. Gizli değerler
 * (SMTP şifresi) koda değil ortam değişkenine yazılır — bkz. kök {@code .env}.
 *
 * <p>{@code enabled=false} iken hiçbir e-posta gönderilmez; bağlantılar sunucu
 * log'una basılır. Böylece SMTP'siz (offline) geliştirme akışı bozulmaz.
 *
 * @param enabled          gönderim açık mı
 * @param from             gönderen adres (SMTP kullanıcısıyla aynı olmalı — Gmail
 *                         başka bir adres adına göndermeye izin vermez)
 * @param fromName         alıcının gördüğü gönderen adı
 * @param baseUrl          kullanıcı arayüzünün kök adresi; doğrulama/sıfırlama
 *                         bağlantıları bunun üstüne kurulur
 * @param verificationTtl  e-posta doğrulama bağlantısının ömrü
 * @param passwordResetTtl şifre sıfırlama bağlantısının ömrü (bilinçli olarak kısa)
 */
@ConfigurationProperties(prefix = "waydee.mail")
public record MailProperties(
        boolean enabled,
        String from,
        String fromName,
        String baseUrl,
        Duration verificationTtl,
        Duration passwordResetTtl
) {
    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "noreply@waydee.com";
        }
        if (fromName == null || fromName.isBlank()) {
            fromName = "Waydee";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:5175";
        }
        // Sondaki eğik çizgi bağlantıda çift slash üretirdi.
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (verificationTtl == null) {
            verificationTtl = Duration.ofHours(24);
        }
        if (passwordResetTtl == null) {
            passwordResetTtl = Duration.ofHours(1);
        }
    }

    /** Doğrulama bağlantısı — arayüzdeki {@code /verify-email} ekranına gider. */
    public String verifyUrl(String token) {
        return baseUrl + "/verify-email?token=" + token;
    }

    /** Şifre sıfırlama bağlantısı — arayüzdeki {@code /reset-password} ekranına gider. */
    public String resetUrl(String token) {
        return baseUrl + "/reset-password?token=" + token;
    }
}
