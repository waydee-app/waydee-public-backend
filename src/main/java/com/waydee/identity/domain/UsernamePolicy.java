package com.waydee.identity.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <b>Kullanıcı adı kuralları — TEK KAYNAK.</b>
 *
 * <p>🔴 17 Ağu 2026'da yazıldı. Öncesinde biçim kuralı <b>üç yerde</b> ayrı ayrı
 * duruyordu ({@code RegisterRequest} anotasyonu, Google'ın ad üretici metodu,
 * arayüz) ve <b>rezerve kelime kontrolü hiç yoktu</b>.
 *
 * <h3>🔴 Rezerve adlar neden ZORUNLU</h3>
 * Vitrin profili {@code /{username}} adresinde yaşıyor ve router'da bu kural
 * <b>en sonda</b> ({@code /:tag}). React Router statik segmenti önceler, yani
 * {@code settings} kullanıcı adını alan biri {@code /settings}'e gittiğinde
 * <b>ayarlar sayfasını</b> görür — o hesabın profiline <b>hiçbir zaman</b>
 * ulaşılamaz. Daha kötüsü: {@code admin}, {@code api}, {@code login} gibi
 * adlar bir saldırgana <b>güven devşirme</b> imkânı verir
 * ({@code waydee.com/admin} bir "resmî" sayfa gibi okunur).
 *
 * <p>⚠️ Liste <b>rotalardan geniş</b> tutuldu: bugün var olmayan ama yarın
 * eklenmesi çok muhtemel adlar (ör. {@code help}, {@code blog}) şimdiden
 * kapatıldı. Sonradan kapatmak, o adı almış bir kullanıcıyı taşımak demektir.
 */
public final class UsernamePolicy {

    /** Kayıt formundaki kuralla birebir aynı. */
    public static final Pattern FORMAT = Pattern.compile("^[a-z0-9_]{3,30}$");

    /**
     * Alınamayan adlar.
     *
     * <p>⚠️ Buraya bir ad eklerken <b>o adı zaten almış kullanıcı var mı</b>
     * diye bak — liste yalnız YENİ seçimleri engeller, mevcut satırları
     * kendiliğinden düzeltmez.
     */
    private static final Set<String> RESERVED = Set.of(
            // Uygulamanın gerçek rotaları (router.tsx'ten sayıldı)
            "about", "ai", "analytics", "forgot_password", "harita", "home",
            "invoices", "kartim", "login", "map", "market", "me", "my_card",
            "notifications", "pazar", "plan", "privacy", "register", "reports",
            "reset_password", "saved", "settings", "terms", "verify_email",
            "auth", "post", "t", "u",
            // Kurumsal / güven devşirmeye açık
            "admin", "administrator", "waydee", "support", "help", "root",
            "system", "official", "team", "staff", "moderator", "mod",
            "security", "billing", "payment", "payments", "legal",
            // Altyapı adları
            "api", "www", "mail", "smtp", "ftp", "cdn", "static", "assets",
            "media", "ws", "webhook", "webhooks", "actuator", "swagger",
            "graphql", "oauth", "sso", "status", "health",
            // Genel
            "null", "undefined", "true", "false", "new", "edit", "delete",
            "search", "explore", "signup", "signin", "logout", "blog", "docs");

    private UsernamePolicy() {
    }

    /** Girdiyi saklanacak biçime çevirir (küçük harf + kırpma). */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean hasValidFormat(String normalized) {
        return FORMAT.matcher(normalized).matches();
    }

    public static boolean isReserved(String normalized) {
        return RESERVED.contains(normalized);
    }

    /** Biçim + rezerve kontrolünden geçiyor mu (benzersizlik AYRI — DB sorar). */
    public static boolean isSelectable(String normalized) {
        return hasValidFormat(normalized) && !isReserved(normalized);
    }
}
