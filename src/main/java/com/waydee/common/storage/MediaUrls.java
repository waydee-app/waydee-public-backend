package com.waydee.common.storage;

import java.util.UUID;

/**
 * DTO'ların statik fabrika metotlarından imzalı medya bağlantısı üretebilmesi için
 * ince bir erişim noktası. Tek örnek uygulama açılışında {@link MediaUrlSigner} tarafından kaydedilir.
 */
public final class MediaUrls {

    private static volatile MediaUrlSigner signer;

    private MediaUrls() {
    }

    static void register(MediaUrlSigner instance) {
        signer = instance;
    }

    /** Medya yoksa null döner; böylece çağıran taraf null kontrolünü tek yerde yapar. */
    public static String of(UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        MediaUrlSigner current = signer;
        if (current == null) {
            throw new IllegalStateException("MediaUrlSigner henüz hazır değil");
        }
        return current.sign(mediaId);
    }

    /**
     * <b>WebGL için</b> aynı orijinden akıtılan medya adresi ({@code &stream=1}).
     *
     * <h3>Neden gerekli</h3>
     * <p>🔴 Normal adres depoya (S3) <b>302</b> ile yönlendirir ve S3
     * <b>CORS başlığı döndürmez</b>. WebGL bir dokuyu ancak CORS onayı varsa
     * kabul eder — bu yüzden 3B mağazada logo, raf fotoğrafları ve televizyon
     * videosu <b>üretimde</b> hiç görünmüyordu (yerelde MinIO her Origin'i
     * yankıladığı için sorun görünmüyordu).
     *
     * <p>⚠️ <b>Yalnız 3B sahnede</b> kullanılır. Vitrin, avatar ve akış
     * görselleri düz {@code <img>} ile çizilir, CORS istemez ve 302'de kalır —
     * yani ölçek kazanımı korunur.
     *
     * <p>⚠️ Kalıcı çözüm S3 kovasına CORS tanımlamaktır; o yapıldığında bu
     * yardımcı {@link #of} ile değiştirilebilir.
     */
    public static String streamed(UUID mediaId) {
        String url = of(mediaId);
        if (url == null) {
            return null;
        }
        /* İmza `id + e` üzerinden hesaplanır; ek parametre onu bozmaz. */
        return url + (url.contains("?") ? "&" : "?") + "stream=1";
    }
}
