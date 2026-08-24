package com.waydee.aistudio.application;

import com.waydee.aistudio.api.dto.AiStudioDtos.FashionModelRequest;
import org.springframework.stereotype.Component;

/**
 * <b>Form → istem</b> (V45).
 *
 * <h3>Neden ayrı bir sınıf</h3>
 * <p>İstem bu ürünün <b>gerçek çıktı kalitesini</b> belirleyen tek yerdir;
 * servisin içine gömülseydi her ince ayar iş mantığına dokunmayı gerektirirdi.
 * Burada durduğu için istemi değiştirmek, akışı hiç açmadan yapılabilir.
 *
 * <h3>🔴 İSTEM OLUMLU DİLLE YAZILIR — 16 Ağu 2026'da ölçüldü</h3>
 * <p>İlk sürüm vurgulu ve <b>olumsuz</b> bir kural listesi taşıyordu:
 * <i>"Reproduce every product EXACTLY… Do not redesign, restyle, recolour,
 * 'improve' or replace… Strict rules: no text, no watermark, no distortion…"</i>
 * Sonuç: sağlayıcı <b>422 {@code no_media_generated}</b> döndürüyordu, yani
 * model <b>hiç görsel üretmiyordu</b> — talimat yığınına muhtemelen metinle
 * karşılık veriyordu. Hata mesajı "unsafe content" ima ettiği için teşhis
 * kolayca yanlış yere gidebilirdi.
 *
 * <p><b>Ölçüm (aynı görsel, aynı oturum, üç istem):</b>
 * <pre>
 *   kısa istem                      → OK
 *   tam istem − "Strict rules"      → FAIL   ← vurgulu "EXACTLY/Do not" bloğu
 *   aynı içerik, olumlu dille       → OK
 * </pre>
 * Yani suçlu uzunluk değil, <b>emir/olumsuzlama yoğunluğuydu</b>.
 *
 * <p><b>Kural:</b> istem <b>sahneyi betimler</b>, kural listesi dayatmaz.
 * "Ürünü değiştirme" yerine "ürün kaynağındaki hâliyle görünür" denir; ikisi
 * aynı şeyi ister, yalnız biri görsel üretir.
 *
 * <h3>Korunan davranışlar</h3>
 * <ul>
 *   <li><b>Ürün sadakati</b> — renk/kumaş/desen/oran sayılarak yazılır (olumlu).
 *       Satıcı için yanlış ürün göstermek, aracı işe yaramaz kılar.</li>
 *   <li><b>Ürün sayısı açıkça yazılır</b> ("the 2 attached product images"):
 *       model görselleri saymazsa bir ürünü atlıyor.</li>
 *   <li><b>Tek kişi</b> — arka planda ikinci bir insan, ürün fotoğrafını
 *       kullanılamaz hâle getiriyor.</li>
 * </ul>
 *
 * <p>⚠️ İstem <b>İngilizcedir ve öyle kalmalı</b>: arayüz beş dilli ama model
 * İngilizce istemde belirgin biçimde daha iyi sonuç veriyor. Kullanıcı zaten
 * istem yazmıyor, <b>seçiyor</b>; dil burada bir arayüz meselesi değil.
 */
@Component
public class FashionPromptBuilder {

    public String build(FashionModelRequest r) {
        int products = r.productMediaIds().size();

        String model = "%s, %s, %s, %s, %s, %s, %s, %s, %s".formatted(
                FashionOptions.describe(FashionOptions.GENDER, r.gender(), "cinsiyet"),
                FashionOptions.describe(FashionOptions.ETHNICITY, r.ethnicity(), "etnisite"),
                FashionOptions.describe(FashionOptions.AGE, r.age(), "yaş"),
                FashionOptions.describe(FashionOptions.SKIN_TONE, r.skinTone(), "ten rengi"),
                FashionOptions.describe(FashionOptions.FACE_TYPE, r.faceType(), "yüz tipi"),
                FashionOptions.describe(FashionOptions.EYE_COLOR, r.eyeColor(), "göz rengi"),
                FashionOptions.describe(FashionOptions.HAIR_COLOR, r.hairColor(), "saç rengi"),
                FashionOptions.describe(FashionOptions.HAIRSTYLE, r.hairstyle(), "saç stili"),
                FashionOptions.describe(FashionOptions.BODY_SIZE, r.bodySize(), "beden"));

        String expression = FashionOptions.describe(FashionOptions.EXPRESSION, r.expression(), "ifade");
        String shot = FashionOptions.describe(FashionOptions.SHOT, r.shot(), "çerçeve");
        String background = FashionOptions.describe(FashionOptions.BACKGROUND, r.background(), "arka plan");

        /* ⚠️ Cümleler BETİMLEYİCİ: "şu olsun" değil "şöyle görünüyor". Emir ve
           olumsuzlama yoğunluğu modeli görsel yerine metne itiyordu (yukarıdaki
           ölçüm). Ürün sadakati de olumlu yazılır. */
        String outfit = products == 1
                ? "The model wears the product from the attached image, faithful to its source: "
                        + "the same colour, fabric, pattern, print, proportions and length."
                : "The model wears all %d attached products together as one coherent outfit, "
                        .formatted(products)
                        + "each faithful to its source image: the same colour, fabric, pattern, "
                        + "print, proportions and length.";

        /* ⚠️ `shot` betimlemesi zaten "a full-body fashion photograph…" diye
           başlıyor; başına ikinci bir "A" koymak "A a full-body…" üretirdi. */
        return """
                %s. %s

                The model is %s, with %s, approximately %d cm tall.
                Setting: %s. Professional studio lighting, soft shadows, \
                sharp focus on the garment, high-end e-commerce catalogue quality, \
                photorealistic, natural skin texture, a single person in the frame.
                """
                .formatted(shot, outfit, model, expression, r.heightCm(), background)
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    /**
     * Çerçeveye uygun en-boy oranı.
     *
     * <p>⚠️ Tam boy çekim <b>3:4</b>, yakın çekim <b>4:5</b>: kare bir tuvalde
     * tam boy manken ya çok küçük kalıyor ya da ayakları kırpılıyordu.
     * Değerler sağlayıcının kabul ettiği kümeden ({@code aspect_ratio} enum'u).
     */
    public String aspectRatio(FashionModelRequest r) {
        return "CLOSE_UP".equals(r.shot()) ? "4:5" : "3:4";
    }
}
