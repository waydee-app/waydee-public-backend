package com.waydee.identity.domain;

/**
 * Hesabın hangi yolla açıldığı.
 *
 * <p>Bu alan "kullanıcı nasıl giriş yapabilir" sorusunun cevabı değildir —
 * ona {@link User#hasPassword()} ve {@link User#getGoogleSub()} birlikte karar
 * verir. Burada tutulan şey hesabın <b>kökeni</b>dir: denetim kaydında ve
 * yönetim ekranında "bu hesap Google ile mi açıldı" sorusunu cevaplar.
 */
public enum AuthProvider {
    /** Kullanıcı adı/e-posta + şifre ile açılmış klasik hesap. */
    LOCAL,
    /** Google ile açılmış hesap (şifresi olmayabilir). */
    GOOGLE
}
