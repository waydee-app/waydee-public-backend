package com.waydee.aistudio.application;

/**
 * <b>Kredi maliyeti — tek gerçek kaynak</b> (V45).
 *
 * <h3>🔴 Neden burada, tek yerde</h3>
 * <p>Maliyet <b>yalnız sunucuda</b> hesaplanır. İstemci "bu işlem kaç kredi"
 * sorusunu <b>aynı formülü tekrar yazarak değil</b>, {@code POST /ai/quote}
 * ucuna sorarak yanıtlar. İki kopya formül olsaydı biri diğerinden sapar ve
 * kullanıcıya "60 kredi" gösterip 120 düşen bir ekran ortaya çıkardı.
 *
 * <h3>Formül</h3>
 * <pre>
 *   maliyet = temel(kalite) + (ürünSayısı - 1) × EK_URUN
 * </pre>
 *
 * <p>Gerekçeler:
 * <ul>
 *   <li><b>Kalite</b> maliyetin ana bileşeni: yüksek kalite ikinci bir
 *       sağlayıcı çağrısı (büyütme) demektir, yani bize de iki kat pahalıdır.</li>
 *   <li><b>Ürün sayısı</b> ek yük getirir: her ürün modele giden ek bir görsel,
 *       yani daha çok jeton.</li>
 * </ul>
 *
 * <p>⚠️ <b>Bir üretim = bir görsel.</b> "Kaç görsel" parametresi bilinçli olarak
 * <b>yok</b>: her görselin kendi satırı, kendi durumu ve kendi iade yolu olmalı.
 * Tek satırda dört görsel üretmek, üçü başarılı biri başarısız olduğunda ne
 * iade edileceği belirsiz bir durum yaratırdı. Dört görsel isteyen kullanıcı
 * dört kez "Oluştur" der ve dördü galeride ayrı ayrı durur.
 *
 * <p>Ölçek duygusu: Pro (2.000 kredi/ay) standart kalitede <b>33</b>, Premium
 * (10.000) <b>166</b> tekil üretim yapar. Yüksek kalitede bunun yarısı.
 */
public final class CreditCost {

    /** Standart kalite — tek sağlayıcı çağrısı (~1K çıktı). */
    public static final int BASE_STANDARD = 60;

    /** Yüksek kalite — üretim + 2× büyütme (~2K çıktı), yani iki çağrı. */
    public static final int BASE_HIGH = 120;

    /** İlkinden sonraki her ürün görseli. */
    public static final int EXTRA_PRODUCT = 10;

    /** Bir üretime konabilecek en fazla ürün (sağlayıcı da çok görselde 4'te iyi). */
    public static final int MAX_PRODUCTS = 4;

    private CreditCost() {
    }

    /**
     * @param highQuality  2K (büyütmeli) mi
     * @param productCount 1..{@link #MAX_PRODUCTS}
     */
    public static int of(boolean highQuality, int productCount) {
        int base = highQuality ? BASE_HIGH : BASE_STANDARD;
        int products = Math.max(1, Math.min(productCount, MAX_PRODUCTS));
        return base + (products - 1) * EXTRA_PRODUCT;
    }
}
