package com.waydee.aistudio.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * <b>fal.ai köprüsü</b> (V45).
 *
 * <h3>Sözleşme — 16 Ağu 2026'da GERÇEK ÇAĞRIYLA ölçüldü</h3>
 * <pre>
 *   POST https://queue.fal.run/{model}        → {"status":"IN_QUEUE",
 *                                                "request_id":"…",
 *                                                "status_url":"…","response_url":"…"}
 *   GET  {status_url}                         → {"status":"IN_QUEUE|IN_PROGRESS|COMPLETED"}
 *   GET  {response_url}                       → {"images":[{"url":"…","width":1024,…}]}
 * </pre>
 * <p>⚠️ Yanıttaki {@code response_url} model yolunun <b>alt kısmını düşürür</b>
 * ({@code fal-ai/nano-banana/edit} → {@code fal-ai/nano-banana/requests/…}).
 * Bu yüzden adres <b>elle kurulmaz</b>, sağlayıcının verdiği kullanılır.
 *
 * <h3>Neden kuyruk API'si, {@code fal.run} değil</h3>
 * <p>Eşzamanlı uç isteği üretim bitene kadar açık tutar. Bir görsel 6–40 saniye
 * sürüyor; o süre boyunca bir Tomcat thread'i (üstelik bizde 200 tane var)
 * bloke kalırdı. Kuyruk API'sinde gönderim <b>anında</b> döner ve yoklama
 * ayrı bir havuzda ({@code aiExecutor}) yapılır.
 *
 * <h3>🔒 Anahtar</h3>
 * <p>Koda yazılmaz: {@code FAL_API_KEY} ortam değişkeninden gelir. Anahtar
 * yoksa istemci <b>yapılandırılmamış</b> sayılır ve stüdyo uçları 503 döner —
 * kredi düşmez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FalClient {

    private final ObjectMapper objectMapper;

    @Value("${waydee.ai.fal.enabled:true}")
    private boolean enabled;

    @Value("${waydee.ai.fal.base-url:https://queue.fal.run}")
    private String baseUrl;

    @Value("${waydee.ai.fal.api-key:}")
    private String apiKey;

    /** Ürün → manken modeli (çok görselli düzenleme). */
    @Value("${waydee.ai.fal.model:fal-ai/nano-banana/edit}")
    private String model;

    /** Yüksek kalitede ikinci geçiş — büyütme. */
    @Value("${waydee.ai.fal.upscale-model:fal-ai/esrgan}")
    private String upscaleModel;

    /** Tek bir üretimin toplam bekleme tavanı. */
    @Value("${waydee.ai.fal.timeout-seconds:240}")
    private int timeoutSeconds;

    /**
     * Girdi görsellerinin yüklendiği <b>fal depolama</b> kökü.
     *
     * <p>⚠️ Kuyruk adresinden ({@code queue.fal.run}) <b>ayrı bir servis</b> ve
     * ayrı bir alan adı; tek bir {@code base-url} ile ifade edilemez.
     */
    @Value("${waydee.ai.fal.storage-url:https://rest.alpha.fal.ai}")
    private String storageUrl;

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * <b>Ürün görselini sağlayıcının deposuna yükler</b> ve adresini döndürür.
     *
     * <h3>🔴 Neden data URI DEĞİL — 16 Ağu 2026'da ölçüldü</h3>
     * <p>İlk uygulama görselleri {@code data:image/jpeg;base64,…} olarak
     * gömüyordu. Sağlayıcı isteği kabul ediyor, kuyruğa alıyor ve sonra
     * <b>422 {@code no_media_generated}</b> döndürüyordu — hata mesajı
     * "unsafe content" ve "missing attachments" gibi <b>yanıltıcı</b> sebepler
     * sayıyor, "data URI desteklenmiyor" demiyor. <b>İzole edilerek kanıtlandı:</b>
     * birebir aynı istem + birebir aynı görsel, yalnız data URI yerine HTTPS
     * adresiyle gönderildiğinde <b>başarıyla</b> üretti.
     *
     * <p>Akış üç adımdır (fal REST):
     * <pre>
     *   POST {storage}/storage/upload/initiate?storage_type=fal-cdn-v3
     *        {"content_type","file_name"}     → {"file_url","upload_url"}
     *   PUT  {upload_url}  &lt;baytlar&gt;            (imzalı, tek kullanımlık)
     *   →    {file_url} artık herkese açık okunabilir
     * </pre>
     *
     * <p>⚠️ <b>Gizlilik bedeli açıkça söylenmeli:</b> ürün fotoğrafı sağlayıcının
     * CDN'ine <b>tahmin edilemez</b> bir adresle çıkar. Bunlar kullanıcının
     * zaten yayımlamak için hazırladığı ürün görselleridir; yine de <b>bizim</b>
     * imzalı medya adresimiz gönderilmiyor — o adres, ömrü boyunca hesabın
     * medya kapısını üçüncü bir tarafa açardı.
     */
    public String uploadImage(byte[] jpeg, String fileName) {
        if (!isConfigured()) {
            throw new FalException("Yapay zekâ sağlayıcısı yapılandırılmamış");
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        ObjectNode init = objectMapper.createObjectNode();
        init.put("content_type", "image/jpeg");
        init.put("file_name", fileName);

        JsonNode ticket = send(http, HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(storageUrl) + "/storage/upload/initiate?storage_type=fal-cdn-v3"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Key " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(write(init)))
                .build());

        String fileUrl = ticket.path("file_url").asText(null);
        String uploadUrl = ticket.path("upload_url").asText(null);
        if (fileUrl == null || uploadUrl == null) {
            throw new FalException("Depo yükleme adresi alınamadı");
        }
        try {
            HttpResponse<String> put = http.send(HttpRequest.newBuilder()
                            .uri(URI.create(uploadUrl))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "image/jpeg")
                            /* ⚠️ İmza adresin İÇİNDE; buraya `Authorization`
                               eklemek imzalı yüklemeyi bozuyor. */
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(jpeg))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (put.statusCode() / 100 != 2) {
                throw new FalException("Ürün görseli sağlayıcıya yüklenemedi (" + put.statusCode() + ")");
            }
        } catch (FalException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalException("Yükleme kesildi");
        } catch (Exception e) {
            throw new FalException("Ürün görseli sağlayıcıya yüklenemedi");
        }
        return fileUrl;
    }

    /**
     * Ürün görsellerini mankene giydirir.
     *
     * @param imageUrls {@link #uploadImage} ile yüklenmiş ürün görsellerinin
     *                  adresleri — <b>data URI KABUL EDİLMEZ</b>, gerekçe orada
     * @return üretilen görsellerin adresleri (sağlayıcıda geçici)
     */
    public Result generateFashion(String prompt, List<String> imageUrls, int imageCount, String aspectRatio) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("prompt", prompt);
        ArrayNode urls = body.putArray("image_urls");
        imageUrls.forEach(urls::add);
        body.put("num_images", imageCount);
        // ⚠️ JPEG: PNG çıktısı 3–4 kat büyük ve depoya biz yazıyoruz.
        body.put("output_format", "jpeg");
        body.put("aspect_ratio", aspectRatio);
        /* Modelin "kaç görsel üreteyim" talimatını istemden çıkarmasını engeller;
           sayıyı biz belirliyoruz ve maliyeti ona göre düştük. */
        body.put("limit_generations", true);

        Call call = runWithRetry(body);
        List<String> images = new java.util.ArrayList<>();
        for (JsonNode img : call.output().path("images")) {
            String url = img.path("url").asText(null);
            if (url != null) {
                images.add(url);
            }
        }
        if (images.isEmpty()) {
            /* 🔴 Boş sonuç bir HATA'dır, boş bir başarı değil. Sessizce
               "başarılı ama görselsiz" bir satır yazılsaydı kullanıcı kredisini
               ödemiş ama iade hakkını kaybetmiş olurdu. */
            throw new FalException("Sağlayıcı görsel döndürmedi (içerik filtresi olabilir)");
        }
        return new Result(images, call.requestId());
    }

    /**
     * 2× büyütme — yüksek kalite modunun ikinci geçişi.
     *
     * <p>⚠️ Çağıran bunu <b>en iyi çaba</b> olarak kullanır: büyütme
     * başarısız olursa temel görsel korunur ve <b>aradaki kredi farkı iade
     * edilir</b>. Kullanıcıyı elinde sonuç varken elleri boş bırakmak, aldığı
     * hizmeti tamamen iptal etmekten daha kötü bir sonuç olurdu.
     */
    public String upscale(String imageUrl) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("image_url", imageUrl);
        body.put("scale", 2);
        body.put("output_format", "jpeg");
        // ⚠️ Model insan yüzü içeriyor; `face` bayrağı yüz detayını korur.
        body.put("face", true);
        String url = run(upscaleModel, body).output().path("image").path("url").asText(null);
        if (url == null) {
            throw new FalException("Büyütme sonucu boş döndü");
        }
        return url;
    }

    /* ------------------------------------------------------------------ altyapı */

    /**
     * <b>Reddedilen üretimi yeniden dener</b> — {@value #MAX_ATTEMPTS} denemeye kadar.
     *
     * <h3>🔴 Neden gerekli — 16 Ağu 2026'da ölçüldü</h3>
     * <p>Model bazen hiçbir görsel üretmiyor ve kuyruk <b>422
     * {@code no_media_generated}</b> döndürüyor. Mesaj "unsafe content" ima
     * ediyor ama <b>gerçek sebep bu değil</b>: kanıt olarak <b>birebir aynı
     * istem</b>, bir ürün fotoğrafında reddedildi, diğerinde başarıyla üretti;
     * reddedilen görsel de sonraki denemede geçti. Yani davranış <b>kararsız</b>
     * (Gemini görsel modellerinde bilinen bir durum).
     *
     * <p>Yeniden denemek, kullanıcı için "bazen çalışmayan" bir düğmeyi
     * "çalışan" bir düğmeye çeviriyor. Tavan üçtür: her deneme bize gerçek bir
     * çıkarım maliyeti demek ve sonsuz denemek, gerçekten yasaklı bir içeriği
     * ısrarla zorlamak olurdu.
     *
     * <p>⚠️ Yeniden deneme <b>YALNIZ</b> bu hata türü için yapılır. Zaman
     * aşımı, kimlik hatası ya da 5xx için denemek, sorunu üçe katlamaktan
     * başka işe yaramaz.
     *
     * <p>⚠️ Kullanıcıya <b>tek bir</b> kredi düşülür; denemeler bizim
     * maliyetimizdir. Kredi zaten istek anında düşüldü.
     */
    private static final int MAX_ATTEMPTS = 3;

    private Call runWithRetry(ObjectNode body) {
        NoMediaException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return run(model, body);
            } catch (NoMediaException e) {
                last = e;
                log.info("fal.ai görsel üretmedi ({}/{}) — yeniden deneniyor", attempt, MAX_ATTEMPTS);
            }
        }
        throw last;
    }

    /**
     * Gönder → yokla → sonucu al.
     *
     * <p>⚠️ Yoklama aralığı <b>artan</b>: ilk saniyelerde sık (sonuç erken
     * gelirse hemen görülsün), sonra seyrek (boşuna istek atmayalım). Sabit
     * 1 saniye, 40 saniyelik bir üretimde 40 gereksiz istek demekti.
     */
    private Call run(String modelId, ObjectNode body) {
        if (!isConfigured()) {
            throw new FalException("Yapay zekâ sağlayıcısı yapılandırılmamış");
        }
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JsonNode submitted = send(http, HttpRequest.newBuilder()
                .uri(URI.create(trimSlash(baseUrl) + "/" + modelId))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Key " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(write(body)))
                .build());

        String statusUrl = submitted.path("status_url").asText(null);
        String responseUrl = submitted.path("response_url").asText(null);
        String requestId = submitted.path("request_id").asText(null);
        if (statusUrl == null || responseUrl == null) {
            throw new FalException("Sağlayıcı kuyruk adresi döndürmedi");
        }

        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        long waitMs = 1_000;
        while (System.nanoTime() < deadline) {
            sleep(waitMs);
            waitMs = Math.min(waitMs + 500, 5_000);
            JsonNode status = send(http, get(statusUrl));
            String state = status.path("status").asText("");
            if ("COMPLETED".equals(state)) {
                return new Call(send(http, get(responseUrl)), requestId);
            }
            /* ⚠️ Bilinmeyen bir durum kodu gelirse DÖNGÜ KIRILMAZ: sağlayıcı
               ileride yeni bir ara durum ekleyebilir ve onu "hata" saymak
               çalışan üretimleri iptal ederdi. Tavan zaten zaman aşımıdır. */
        }
        throw new FalException("Üretim zaman aşımına uğradı (" + timeoutSeconds + " sn)");
    }

    private HttpRequest get(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Key " + apiKey)
                .GET()
                .build();
    }

    private JsonNode send(HttpClient http, HttpRequest request) {
        try {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                /* ⚠️ Gövde LOG'a girer ama kullanıcıya gitmez: sağlayıcının
                   hata gövdesi bazen istemin tamamını yankılıyor. */
                log.warn("fal.ai {} döndü: {}", res.statusCode(), truncate(res.body()));
                /* 🔴 "Görsel üretilmedi" AYRI bir hata türüdür: tek yeniden
                   denemeyi hak eden durum budur (gerekçe `runWithRetry`ta).
                   Ayırt etmezsek ya hiç denemeyiz ya da zaman aşımını da
                   üç kez tekrarlayıp kullanıcıyı üç kat bekletiriz. */
                if (res.body() != null && res.body().contains("no_media_generated")) {
                    throw new NoMediaException();
                }
                throw new FalException("Sağlayıcı hatası (" + res.statusCode() + ")");
            }
            return objectMapper.readTree(res.body());
        } catch (FalException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalException("Üretim kesildi");
        } catch (Exception e) {
            throw new FalException("Sağlayıcıya ulaşılamadı: " + e.getClass().getSimpleName());
        }
    }

    private String write(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new FalException("İstek gövdesi hazırlanamadı");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalException("Üretim kesildi");
        }
    }

    private static String truncate(String v) {
        return v == null ? "" : v.substring(0, Math.min(v.length(), 400));
    }

    private static String trimSlash(String v) {
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    /** Tek bir kuyruk çağrısının sonucu — gövde + kuyruk kimliği. */
    private record Call(JsonNode output, String requestId) {
    }

    /** @param requestId sağlayıcının kuyruk kimliği (destek/teşhis için) */
    public record Result(List<String> imageUrls, String requestId) {
    }

    /** Sağlayıcı kaynaklı hata — çağıran bunu yakalayıp <b>krediyi iade eder</b>. */
    public static class FalException extends RuntimeException {
        public FalException(String message) {
            super(message);
        }
    }

    /**
     * Model hiç görsel üretmedi ({@code no_media_generated}).
     *
     * <p>⚠️ Mesaj <b>kullanıcıya gösterilecek</b> hâliyle yazılmıştır ve
     * sağlayıcının "unsafe content" imasını <b>tekrarlamaz</b>: ölçüldüğü
     * kadarıyla sebep içerik değil, modelin kararsızlığı. Kullanıcıya "içeriğin
     * uygunsuz" demek, sıradan bir tişört fotoğrafı yükleyen satıcıyı haksız
     * yere suçlamak olurdu.
     */
    public static class NoMediaException extends FalException {
        public NoMediaException() {
            super("Model bu görsel için sonuç üretemedi. Farklı bir ürün fotoğrafı ya da ayar dene.");
        }
    }
}
