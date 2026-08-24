package com.waydee.marketplace.domain;

import java.util.List;

/**
 * Pazar yeri türü.
 *
 * Tür yalnız bir etiket değildir: başvuru formunun <b>mantıklı varsayılanını</b>
 * belirler. Admin isterse her alanı tek tek değiştirebilir ({@code formSchema}),
 * ama hiçbir şey ayarlamazsa tür kendi başına doğru formu üretir — "yürüyüş
 * etkinliği" seçen bir yönetici tarih ve buluşma noktası sormak zorunda
 * kalmadan doğru formu alır.
 */
public enum MarketplaceKind {

    GENERAL("Genel", "Serbest katılım", List.of()),

    STARTUP("Girişim vitrini", "Projeler ve startuplar",
            List.of(Field.STAGE, Field.WEBSITE, Field.FOUNDED_YEAR, Field.TEAM_SIZE, Field.LOOKING_FOR)),

    LISTING("İlan / alım-satım", "Ürün ve hizmet ilanları",
            List.of(Field.PRICE, Field.CONDITION, Field.CONTACT_PHONE, Field.GALLERY)),

    EVENT("Etkinlik", "Tarihli etkinlikler",
            List.of(Field.STARTS_AT, Field.ENDS_AT, Field.LOCATION, Field.CAPACITY, Field.PRICE)),

    TOUR("Gezi / ziyaret", "Rehberli gezi ve ziyaretler",
            List.of(Field.STARTS_AT, Field.LOCATION, Field.CAPACITY, Field.PRICE, Field.CONTACT_PHONE)),

    WALK("Yürüyüş", "Doğa ve şehir yürüyüşleri",
            List.of(Field.STARTS_AT, Field.LOCATION, Field.CAPACITY, Field.GALLERY)),

    FOOD("Yeme-içme", "Lezzet noktaları",
            List.of(Field.PRICE, Field.LOCATION, Field.CONTACT_PHONE, Field.GALLERY)),

    ART("Sanat & tasarım", "Eser ve atölyeler",
            List.of(Field.GALLERY, Field.WEBSITE, Field.PRICE));

    /** Formda açılıp kapatılabilen hazır alanlar. */
    public enum Field {
        TAGLINE, WEBSITE, CONTACT_EMAIL, CONTACT_PHONE, LOGO, COVER, GALLERY,
        STAGE, FOUNDED_YEAR, TEAM_SIZE, LOOKING_FOR,
        STARTS_AT, ENDS_AT, LOCATION, CAPACITY, PRICE, CONDITION
    }

    /**
     * Her türde her zaman açık olan çekirdek alanlar.
     *
     * <p>🔴 15 Ağu 2026 — {@code WEBSITE} ve {@code CONTACT_PHONE} eklendi.
     *
     * <p>Yeni başvuru formu <b>her pazarda</b> dört şey soruyor: tek cümlelik
     * tanıtım · logo · website · telefon (kullanıcı isteği). Bu ikisi
     * {@code ALWAYS_ON} listesinde olmadığı için {@code applyBaseContent}
     * onları <b>sessizce null'a çekiyordu</b>: form gönderiliyor, kayıt
     * başarılı görünüyor, ama website ve telefon <b>kayboluyordu</b>.
     *
     * <p>⚠️ Ölçüldü (15 Ağu, GENERAL pazarda): dört alanla yapılan başvuruda
     * yanıt {@code "website": null} döndü. Alan kapalıysa null'a çekme kuralı
     * doğru — eksik olan, yeni formun alanlarının açık sayılmasıydı.
     */
    public static final List<Field> ALWAYS_ON = List.of(
            Field.TAGLINE, Field.LOGO, Field.COVER, Field.WEBSITE, Field.CONTACT_PHONE);

    private final String label;
    private final String hint;
    private final List<Field> extras;

    MarketplaceKind(String label, String hint, List<Field> extras) {
        this.label = label;
        this.hint = hint;
        this.extras = extras;
    }

    public String label() {
        return label;
    }

    public String hint() {
        return hint;
    }

    /** Türün varsayılan olarak AÇTIĞI alanlar (çekirdek + türe özgü). */
    public List<Field> defaultFields() {
        return java.util.stream.Stream.concat(ALWAYS_ON.stream(), extras.stream()).toList();
    }
}
