package com.waydee.messaging.domain;

import java.util.UUID;

/**
 * UUID sıralaması — <b>PostgreSQL ile birebir aynı</b>.
 *
 * <p>⚠️ <b>Neden var (2 Ağu 2026'da üretimde 500 verdi):</b> {@code conversations}
 * tablosu çifti sıralı tutar ({@code CHECK (user_lo_id < user_hi_id)}), ama
 * Java ile PostgreSQL bu karşılaştırmayı <b>farklı</b> yapar:
 * <ul>
 *   <li>{@link UUID#compareTo} en anlamlı 64 biti <b>işaretli long</b> olarak
 *       karşılaştırır → üst biti 1 olan bir UUID (ör. {@code f...}) Java'da
 *       <b>negatif</b>, yani "küçük" sayılır.</li>
 *   <li>PostgreSQL {@code uuid} tipini 16 baytlık <b>işaretsiz</b> dizi olarak
 *       karşılaştırır (memcmp) → aynı UUID orada "büyük"tür.</li>
 * </ul>
 * Sonuç: üst bitleri farklı iki kullanıcı sohbet açmaya çalıştığında Java
 * doğru sırayı bulduğunu sanıyor, INSERT ise CHECK'e takılıyordu
 * ({@code violates check constraint "ck_conversations_order"} → 500).
 * Kullanıcıların yaklaşık <b>yarısı</b> birbirine mesaj atamıyordu.
 *
 * <p>Çözüm: karşılaştırmayı hex metin üzerinden yapmak. Kanonik (küçük harf,
 * sabit uzunluklu) gösterimde sözlük sırası, baytların işaretsiz sırasıyla
 * birebir aynıdır — yani PostgreSQL'in kullandığı sıra.
 */
public final class UuidOrder {

    private UuidOrder() {
    }

    /** PostgreSQL'in {@code <} operatörüyle aynı sonucu verir. */
    public static int compare(UUID a, UUID b) {
        return a.toString().compareTo(b.toString());
    }

    /** Çiftin küçük olanı ({@code user_lo_id}). */
    public static UUID lo(UUID a, UUID b) {
        return compare(a, b) <= 0 ? a : b;
    }

    /** Çiftin büyük olanı ({@code user_hi_id}). */
    public static UUID hi(UUID a, UUID b) {
        return compare(a, b) <= 0 ? b : a;
    }
}
