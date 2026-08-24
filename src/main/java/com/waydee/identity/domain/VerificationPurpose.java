package com.waydee.identity.domain;

/**
 * Bir doğrulama jetonunun ne işe yaradığı.
 *
 * <p>Amaç jetonun içinde değil <b>satırında</b> tutulur: böylece bir akış için
 * üretilen jeton başka bir akışta kullanılamaz (doğrulama bağlantısıyla şifre
 * sıfırlanamaz).
 */
public enum VerificationPurpose {
    /** Kayıt sonrası hesabın e-posta adresinin doğrulanması. */
    EMAIL_VERIFY,
    /** Mevcut hesabın e-posta adresinin değiştirilmesi (yeni adrese gider). */
    EMAIL_CHANGE,
    /** Şifremi unuttum akışı. */
    PASSWORD_RESET,
    /**
     * Google dönüşünde üretilen <b>tek kullanımlık takas bileti</b>.
     *
     * <p>⚠️ Neden gerekli: Google akışı bir <b>yönlendirme</b> ile biter ve
     * oturum jetonları yönlendirme adresine konamaz — adres çubuğunda,
     * tarayıcı geçmişinde ve {@code Referer} başlığında sızardı. Bunun yerine
     * sunucu kısa ömürlü, anlamsız bir bilet üretir; arayüz onu <b>POST</b> ile
     * jetona çevirir. Bilet burada da hash'li, süreli ve tek kullanımlıktır.
     */
    OAUTH_EXCHANGE
}
