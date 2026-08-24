package com.waydee.aistudio.application;

import com.waydee.common.error.ApiException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Manken ayarlarının sözlüğü</b> — kod → İngilizce betimleme (V45).
 *
 * <h3>Neden serbest metin yok</h3>
 * <p>🔴 Kullanıcı istemi <b>yazmaz</b>, yalnız <b>seçer</b>. Serbest bir istem
 * alanı üç kapıyı birden açardı:
 * <ul>
 *   <li><b>İstem enjeksiyonu</b> — "yukarıdaki talimatları yok say" yazan bir
 *       kullanıcı modeli ürün fotoğrafçılığından çıkarabilirdi;</li>
 *   <li><b>Kötüye kullanım</b> — gerçek kişilerin adları, müstehcen içerik,
 *       üçüncü kişilerin görselleri;</li>
 *   <li><b>Öngörülemezlik</b> — aynı ayarların aynı işi yapacağı garanti
 *       edilemez, yani maliyeti önceden söylemek de imkânsızlaşırdı.</li>
 * </ul>
 * Referans ürün (vibedesign) de aynı yolu izliyor: form var, istem yok.
 *
 * <h3>Kod ↔ etiket ayrımı</h3>
 * <p>⚠️ Buradaki değerler <b>modele giden İngilizce betimlemelerdir</b>,
 * kullanıcıya gösterilen etiketler <b>değildir</b>. Arayüz kodu alır ve
 * sözlüğünden (5 dil) kendi etiketini basar — vault kuralı: <i>"sunucudan gelen
 * {@code *Label} alanlarını çok dilli arayüzde basma"</i> (83. tur).
 *
 * <p>⚠️ Sıra <b>korunur</b> ({@link LinkedHashMap}): açılır listenin sırası
 * ekranda anlamlı (açıktan koyuya, gençten yaşlıya) ve sunucu ile istemcide
 * aynı olmalı.
 */
public final class FashionOptions {

    private FashionOptions() {
    }

    public static final Map<String, String> GENDER = ordered(
            "FEMALE", "a female fashion model",
            "MALE", "a male fashion model",
            "NEUTRAL", "an androgynous fashion model");

    public static final Map<String, String> ETHNICITY = ordered(
            "INTERNATIONAL", "international, ethnically ambiguous features",
            "EUROPEAN", "European features",
            "SCANDINAVIAN", "Scandinavian features",
            "MEDITERRANEAN", "Mediterranean features",
            "MIDDLE_EASTERN", "Middle Eastern features",
            "EAST_ASIAN", "East Asian features",
            "SOUTH_ASIAN", "South Asian features",
            "AFRICAN", "African features",
            "LATIN", "Latin American features");

    /**
     * ⚠️ <b>Çocuk yaş aralıkları BİLİNÇLİ OLARAK YOK.</b> Referans üründe
     * "6-9 yaş" gibi seçenekler var; burada <b>yetişkin</b> aralıkları ile
     * sınırlandırıldı. Gerçekçi çocuk görselleri üreten bir araç, kötüye
     * kullanım riski taşır ve moderasyonu bizim ölçeğimizde yapılamaz.
     * Çocuk giyimi satan bir satıcı ürününü mankensiz çekimle paylaşabilir.
     */
    public static final Map<String, String> AGE = ordered(
            "A18_24", "18 to 24 years old",
            "A25_34", "25 to 34 years old",
            "A35_44", "35 to 44 years old",
            "A45_54", "45 to 54 years old",
            "A55_PLUS", "55 years or older");

    public static final Map<String, String> SKIN_TONE = ordered(
            "FAIR", "fair skin",
            "LIGHT", "light skin",
            "MEDIUM", "medium skin",
            "OLIVE", "olive skin",
            "TAN", "tanned skin",
            "DEEP", "deep skin");

    public static final Map<String, String> FACE_TYPE = ordered(
            "SOFT", "soft, rounded facial structure",
            "OVAL", "oval face",
            "ANGULAR", "angular, sharp cheekbones",
            "SQUARE", "square jawline",
            "HEART", "heart-shaped face");

    public static final Map<String, String> EYE_COLOR = ordered(
            "DARK_BROWN", "dark brown eyes",
            "BROWN", "brown eyes",
            "HAZEL", "hazel eyes",
            "GREEN", "green eyes",
            "BLUE", "blue eyes",
            "GREY", "grey eyes");

    public static final Map<String, String> EXPRESSION = ordered(
            "SOFT_NEUTRAL", "a soft neutral expression",
            "CONFIDENT", "a confident expression",
            "SMILING", "a natural smile",
            "SERIOUS", "a serious editorial expression");

    public static final Map<String, String> HAIR_COLOR = ordered(
            "BLACK", "black hair",
            "DARK_BROWN", "dark brown hair",
            "BROWN", "brown hair",
            "CHESTNUT", "chestnut hair",
            "BLONDE", "blonde hair",
            "PLATINUM", "platinum blonde hair",
            "RED", "red hair",
            "GREY", "grey hair");

    public static final Map<String, String> HAIRSTYLE = ordered(
            "SLICKED_BACK", "slicked-back hair",
            "STRAIGHT_LONG", "long straight hair",
            "WAVY_LONG", "long wavy hair",
            "BOB", "a bob cut",
            "PONYTAIL", "hair in a ponytail",
            "BUN", "hair in a low bun",
            "CURLY", "curly hair",
            "SHORT", "short cropped hair",
            "BUZZ", "a buzz cut");

    public static final Map<String, String> BODY_SIZE = ordered(
            "XS", "an XS petite build",
            "S", "a slim S build",
            "M", "an average M build",
            "L", "a fuller L build",
            "XL", "a plus-size XL build",
            "XXL", "a plus-size XXL build");

    /** Çekim çerçevesi — istemin kompozisyon cümlesini de bu belirler. */
    public static final Map<String, String> SHOT = ordered(
            "FULL_BODY", "a full-body fashion photograph, head to toe, the whole outfit visible",
            "CLOSE_UP", "a waist-up close-up fashion photograph focusing on the product detail");

    public static final Map<String, String> BACKGROUND = ordered(
            "STUDIO_GREY", "a seamless light grey studio background",
            "STUDIO_WHITE", "a seamless white studio background",
            "STUDIO_BEIGE", "a seamless warm beige studio background",
            "URBAN", "a softly blurred urban street background",
            "INTERIOR", "a minimal modern interior background",
            "NATURE", "a softly blurred natural outdoor background");

    /**
     * Doğrular ve <b>betimlemeyi</b> döndürür.
     *
     * <p>🔴 Bilinmeyen kod <b>400</b> ile reddedilir, sessizce varsayılana
     * düşülmez: kullanıcı seçtiğini sandığı ayarın uygulanmadığını asla fark
     * etmez ve krediyi yine öderdi.
     */
    public static String describe(Map<String, String> catalogue, String code, String field) {
        String description = catalogue.get(code);
        if (description == null) {
            throw ApiException.badRequest("Geçersiz " + field + " değeri: " + code);
        }
        return description;
    }

    /** Arayüzün açılır listeleri için kod listesi (etiketler istemcinin sözlüğünde). */
    public static Map<String, List<String>> catalogue() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        out.put("gender", List.copyOf(GENDER.keySet()));
        out.put("ethnicity", List.copyOf(ETHNICITY.keySet()));
        out.put("age", List.copyOf(AGE.keySet()));
        out.put("skinTone", List.copyOf(SKIN_TONE.keySet()));
        out.put("faceType", List.copyOf(FACE_TYPE.keySet()));
        out.put("eyeColor", List.copyOf(EYE_COLOR.keySet()));
        out.put("expression", List.copyOf(EXPRESSION.keySet()));
        out.put("hairColor", List.copyOf(HAIR_COLOR.keySet()));
        out.put("hairstyle", List.copyOf(HAIRSTYLE.keySet()));
        out.put("bodySize", List.copyOf(BODY_SIZE.keySet()));
        out.put("shot", List.copyOf(SHOT.keySet()));
        out.put("background", List.copyOf(BACKGROUND.keySet()));
        return out;
    }

    /**
     * ⚠️ {@code Map.of} / {@code Map.copyOf} <b>kullanılmaz</b>: ikisi de sırayı
     * korumaz ve açılır listedeki mantıklı sıra (açıktan koyuya, gençten yaşlıya)
     * kaybolurdu. Ayrıca {@code Map.of} null anahtarda {@code get} çağrısında
     * bile NPE atar (83. turda ısırdı) — burada anahtarlar istemciden geliyor.
     */
    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
