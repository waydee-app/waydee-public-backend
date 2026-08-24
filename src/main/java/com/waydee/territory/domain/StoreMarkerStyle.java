package com.waydee.territory.domain;

/**
 * Haritadaki mağaza işaretçisinin <b>tasarımı</b> (V51, 21 Ağu 2026).
 *
 * <p>İşaretçi artık 3B bina değil, <b>halkalı profil fotoğrafı</b>. Halkanın
 * <b>rengi</b> ayrı bir alandır ({@code strokeColor}) — burada seçilen şey
 * halkanın <b>nasıl davrandığıdır</b>.
 *
 * <p>⚠️ Üç seçenek bilinçlidir. Daha fazlası, haritada birbirinden ayırt
 * edilemeyen ama seçim ekranını şişiren varyantlar üretirdi; daha azı ise
 * "tasarım seçebilsin" isteğini bir açma/kapama düğmesine indirgerdi.
 *
 * <p>⚠️ Adlar veritabanına <b>dize olarak</b> yazılıyor
 * ({@code EnumType.STRING}); yeniden adlandırmak eski satırları okunmaz yapar.
 */
public enum StoreMarkerStyle {

    /**
     * <b>Varsayılan</b> — yumuşak nabız. Halkanın dışındaki parıltı sakin bir
     * ritimde büyüyüp küçülür; fotoğraftaki tasarımın kendisi.
     */
    PULSE,

    /** Daha geniş ve güçlü parıltı — kalabalık haritada öne çıkar. */
    GLOW,

    /**
     * Nabız yok: sabit, ince bir parıltı.
     *
     * <p>⚠️ Bu bir <b>erişilebilirlik</b> seçeneğidir de: hareketten rahatsız
     * olan kullanıcı kendi işaretçisini durdurabilmeli.
     */
    SOFT;

    /** Varsayılan tasarım — {@code null} (kullanıcı seçmedi) bunun yerine geçer. */
    public static final StoreMarkerStyle DEFAULT = PULSE;

    /**
     * Dizeyi tasarıma çevirir; tanınmayan/boş değer varsayılana düşer.
     *
     * <p>⚠️ {@code valueOf} doğrudan kullanılmıyor: eski bir istemci ya da
     * elle düzenlenmiş bir satır tanımsız bir ad taşıyabilir ve o durumda
     * <b>haritanın çizilmemesi</b> yerine varsayılana düşmesi doğrudur —
     * işaretçi bir süs değil, mağazanın haritadaki varlığıdır.
     */
    public static StoreMarkerStyle parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
