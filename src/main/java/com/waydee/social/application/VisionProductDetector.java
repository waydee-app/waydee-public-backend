package com.waydee.social.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * <b>Ürün algılama — görsel-dil modeli (VLM) köprüsü.</b>
 *
 * <p>🔴 <b>Neden eklendi (10 Ağu 2026, kullanıcı):</b> <i>"bu eklediğin hiç
 * kullanışlı değil, yüksek doğruluk lazım, gerçekten ürüne yapışması ve ürünü
 * algılaması lazım"</i>. Haklı bir eleştiri ve sebebi yöntemseldi: yerleşik
 * analiz <b>dikkat haritası</b> (saliency) üretir — "bu fotoğrafta göz nereye
 * takılır" sorusunu yanıtlar. <b>"Bu bir çanta mı?"</b> sorusunu yanıtlamaz.
 * Parlak bir gökyüzü ya da kontrastlı bir gölge de tepe noktası üretebilir; o
 * yüzden etiket ürünün üstüne değil "ilginç piksele" düşüyordu.
 *
 * <p>Doğru araç bir <b>nesne algılayıcıdır</b>. Burada bir VLM kullanılır:
 * <ul>
 *   <li><b>kutu</b> döndürür → etiket ürünün <b>merkezine</b> oturur ("yapışır"),</li>
 *   <li><b>ad</b> döndürür → "Krem ceket" gibi; etiket formu önceden dolar,</li>
 *   <li>COCO benzeri 80 sınıfla sınırlı değildir — moda, kozmetik, mobilya,
 *       takı gibi Waydee'nin gerçek içeriğini de adlandırır.</li>
 * </ul>
 *
 * <p>⚠️ <b>Anahtar yoksa hiçbir şey yapmaz</b> ({@code null} döner) ve çağıran
 * yerleşik analize düşer. Uydurma sonuç üretilmez.
 *
 * <p>⚠️ Uç <b>OpenAI uyumlu</b> {@code /chat/completions} sözleşmesidir; bu
 * sözleşmeyi OpenAI, OpenRouter, Groq, Together ve yerel bir vLLM/Ollama
 * sunucusu da konuşur. Yani <b>tek bir sağlayıcıya bağlanmadık</b>: adres ve
 * model adı yapılandırmadan gelir.
 *
 * <p>🔒 Görsel <b>küçültülerek</b> gönderilir (uzun kenar 768px): hem maliyet
 * hem gizlilik için gereğinden büyük veri dışarı çıkmamalı. Görsel hiçbir yere
 * kaydedilmez, yalnız istek gövdesinde geçer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisionProductDetector {

    /** Modele gönderilen görselin uzun kenarı. */
    private static final int SEND_SIZE = 768;
    /** En fazla kaç ürün istenir — etiket formu bunun altında zaten kalıyor. */
    private static final int MAX_ITEMS = 6;

    /**
     * ⚠️ İstem (prompt) <b>koordinat sözleşmesini</b> açıkça yazar: 0–1 arası
     * <b>oran</b>, kutunun <b>merkezi</b>. Piksel istenseydi model gönderilen
     * kopyanın ölçüsüne göre yanıtlar ve orijinale ölçeklemek gerekirdi;
     * oran, ölçekten bağımsızdır (bizim etiketlerimiz de yüzdedir — V29).
     * ⚠️ "Emin değilsen listeye ekleme" cümlesi bilinçli: yanlış etiket,
     * eksik etiketten daha kötüdür.
     */
    private static final String PROMPT = """
            You are a product tagger for a shoppable-photo platform.
            Find the physical products a shopper could buy in this photo
            (clothing, shoes, bags, jewellery, watches, cosmetics, furniture,
            electronics, accessories). Ignore people, faces, body parts,
            backgrounds, buildings, plants and text.

            Answer with STRICT JSON only, no prose, no markdown:
            {"products":[{"name":"short product name","x":0.0,"y":0.0,"w":0.0,"h":0.0,"confidence":0.0}]}

            Rules:
            - x,y = CENTER of the product, as a RATIO of image width/height (0..1).
            - w,h = product box size as a ratio (0..1).
            - name = 1-3 words, the product itself (e.g. "leather boot"), not a description.
            - confidence = 0..1, your certainty that this is a distinct buyable product.
            - At most %d items, most prominent first.
            - If you are not sure something is a product, leave it out.
            - If there is no product at all, answer {"products":[]}.
            """.formatted(MAX_ITEMS);

    private final ObjectMapper objectMapper;

    @Value("${waydee.ai.vision.enabled:true}")
    private boolean enabled;

    @Value("${waydee.ai.vision.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${waydee.ai.vision.api-key:}")
    private String apiKey;

    @Value("${waydee.ai.vision.model:gpt-4o-mini}")
    private String model;

    @Value("${waydee.ai.vision.timeout-seconds:25}")
    private int timeoutSeconds;

    /** Yapılandırılmış mı? Arayüzün "hangi motor çalışıyor" bilgisi de bundan. */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * @return algılanan ürünler; <b>yapılandırılmamışsa ya da çağrı
     *         başarısızsa {@code null}</b> (çağıran yerleşik analize düşer).
     *         Boş liste ise "model baktı, ürün bulamadı" demektir — bu bir
     *         sonuçtur ve yerleşik analizle DEĞİŞTİRİLMEZ.
     */
    public List<Detected> detectOrNull(BufferedImage image) {
        if (!isConfigured() || image == null) {
            return null;
        }
        try {
            String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg(image));
            HttpResponse<String> res = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create(trimSlash(baseUrl) + "/chat/completions"))
                                    .timeout(Duration.ofSeconds(timeoutSeconds))
                                    .header("Content-Type", "application/json")
                                    .header("Authorization", "Bearer " + apiKey)
                                    .POST(HttpRequest.BodyPublishers.ofString(body(dataUrl)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("Görsel modeli {} döndü — yerleşik analize düşülüyor", res.statusCode());
                return null;
            }
            return parse(res.body());
        } catch (Exception e) {
            // ⚠️ Hata YUTULUR: dış servisin kapalı olması kullanıcının akışını
            // kesmemeli; yerleşik analiz devreye girer.
            log.warn("Görsel modeli çağrılamadı — yerleşik analize düşülüyor: {}", e.toString());
            return null;
        }
    }

    private String body(String dataUrl) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        // ⚠️ Yaratıcılık İSTENMEZ: ölçüm yapıyoruz, hikâye yazmıyoruz.
        root.put("temperature", 0);
        root.put("max_tokens", 700);
        // Destekleyen sağlayıcılarda JSON'u şemaya zorlar; desteklemeyen
        // sağlayıcı bu alanı yok sayar (istem zaten JSON istiyor).
        root.putObject("response_format").put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", PROMPT);
        ObjectNode img = content.addObject();
        img.put("type", "image_url");
        img.putObject("image_url").put("url", dataUrl).put("detail", "high");
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Yanıttaki JSON'u okur ve <b>doğrular</b>.
     *
     * <p>⚠️ Model bazen JSON'u ``` çitleri içinde döndürür; ilk `{` ile son `}`
     * arası kesilir. ⚠️ Aralık dışına taşan koordinatlar <b>kırpılır</b>, ad
     * boşsa öğe atılır — güvenilmeyen bir kaynaktan gelen sayı doğrudan
     * arayüze verilmez.
     */
    private List<Detected> parse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        if (s < 0 || e <= s) {
            return List.of();
        }
        JsonNode json = objectMapper.readTree(text.substring(s, e + 1));
        JsonNode items = json.path("products");
        if (!items.isArray()) {
            return List.of();
        }
        List<Detected> out = new ArrayList<>();
        for (JsonNode it : items) {
            String name = it.path("name").asText("").trim();
            if (name.isEmpty() || name.length() > 60) {
                continue;
            }
            double x = clamp01(it.path("x").asDouble(-1));
            double y = clamp01(it.path("y").asDouble(-1));
            if (x < 0 || y < 0) {
                continue;
            }
            double conf = clamp01(it.path("confidence").asDouble(0.5));
            // Düşük güvenli öneriyi hiç göstermiyoruz: kullanıcı yanlış
            // etiketleri elemek zorunda kalırsa araç yük olur, yardım değil.
            if (conf < 0.35) {
                continue;
            }
            out.add(new Detected(name, x, y,
                    clamp01(it.path("w").asDouble(0)), clamp01(it.path("h").asDouble(0)), conf));
            if (out.size() >= MAX_ITEMS) {
                break;
            }
        }
        return out;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return -1;
        }
        return Math.max(0, Math.min(1, v));
    }

    private static String trimSlash(String v) {
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    /** Uzun kenarı {@link #SEND_SIZE} olacak şekilde küçültüp JPEG'e çevirir. */
    private static byte[] jpeg(BufferedImage src) throws Exception {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) SEND_SIZE / Math.max(w, h));
        BufferedImage out = src;
        if (scale < 1.0) {
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            var g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
        } else if (src.getType() != BufferedImage.TYPE_INT_RGB) {
            // ⚠️ PNG'den gelen alfa kanalı JPEG yazıcısını bozuyor (siyah kare).
            out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            var g = out.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return bos.toByteArray();
    }

    /** Algılanan ürün — {@code x,y} merkez oranı, {@code w,h} kutu oranı. */
    public record Detected(String name, double x, double y, double w, double h, double confidence) {
    }
}
