package com.waydee.aistudio.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waydee.aistudio.api.dto.AiStudioDtos.FashionModelRequest;
import com.waydee.social.application.MediaService;
import com.waydee.social.domain.MediaObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>Üretimi arka planda koşturur</b> (V45).
 *
 * <p>🔴 <b>Her hata yolu krediyi iade eder.</b> Bu sınıfta yakalanmayan tek bir
 * istisna, kullanıcının kredisini karşılıksız yakar. Bu yüzden gövde tek bir
 * {@code try/catch(Throwable)} ile sarılıdır — {@code Exception} değil
 * {@code Throwable}: bir {@code Error} da iadeyi atlatmamalı.
 *
 * <p>⚠️ Durum yazıları bu sınıfta <b>değil</b> ({@link AiGenerationStore});
 * gerekçe orada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGenerationRunner {

    /**
     * Sağlayıcıya gönderilen ürün görselinin uzun kenarı.
     *
     * <p>⚠️ Küçültme <b>zorunlu</b>: 10 MB'lık dört fotoğraf base64'e çevrilince
     * ~53 MB'lık bir istek gövdesi eder. Sağlayıcı ya reddeder ya çok yavaş
     * işler; üstelik model bu çözünürlüğü zaten kullanmıyor.
     */
    private static final int SEND_SIZE = 1280;

    /** Sonuç görselini indirirken kabul edilen tavan (medya tavanıyla aynı). */
    private static final int MAX_RESULT_BYTES = 10 * 1024 * 1024;

    private final AiGenerationStore store;
    private final MediaService mediaService;
    private final FalClient falClient;
    private final FashionPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    /**
     * ⚠️ {@code AFTER_COMMIT}: satır ve kredi düşümü kesinleşmeden başlanmaz.
     * Sıra — commit → olay → {@code aiExecutor} havuzu. Doğrudan çağrılsaydı
     * arka plan thread'i satırı hiç göremezdi (kendi bağlantısı, kendi görüşü).
     */
    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(AiGenerationQueuedEvent event) {
        run(event.generationId());
    }

    /**
     * ⚠️ {@code @Transactional} <b>YOK</b> ve olmamalı: gövde dakikalarca
     * sürebilen ağ çağrıları içeriyor. Veritabanına dokunan her adım
     * {@link AiGenerationStore} üzerinden kendi kısa transaction'ında koşar.
     */
    void run(UUID generationId) {
        AiGenerationStore.Snapshot snapshot = store.snapshot(generationId);
        if (snapshot == null) {
            log.warn("Üretim satırı bulunamadı: {}", generationId);
            return;
        }
        try {
            // ⚠️ Çözümleme `try`'ın İÇİNDE: bozuk bir satır da krediyi iade etmeli.
            FashionModelRequest params = objectMapper.readValue(
                    snapshot.paramsJson(), FashionModelRequest.class);
            List<String> productUrls = readProducts(snapshot.inputMediaIds());

            FalClient.Result result = falClient.generateFashion(
                    snapshot.prompt(), productUrls, 1, promptBuilder.aspectRatio(params));
            store.markRunning(generationId, result.requestId());

            String imageUrl = result.imageUrls().get(0);
            if (params.highQuality()) {
                /* ⚠️ EN İYİ ÇABA: büyütme başarısız olursa temel görsel korunur
                   ve ARADAKİ FARK iade edilir. Elinde sonuç varken kullanıcıyı
                   eli boş bırakmak, hizmeti tamamen iptal etmekten daha kötü
                   bir sonuç olurdu. */
                try {
                    imageUrl = falClient.upscale(imageUrl);
                } catch (RuntimeException e) {
                    log.warn("Büyütme başarısız, fark iade ediliyor: {}", e.toString());
                    store.refundUpscaleDelta(generationId, snapshot.userId());
                }
            }

            MediaObject media = mediaService.storeGenerated(snapshot.userId(), download(imageUrl), "image/jpeg");
            store.markSucceeded(generationId, media.getId());
            log.info("Yapay zekâ üretimi tamamlandı: {} ({} kredi)", generationId, snapshot.creditCost());
        } catch (Throwable t) {
            log.warn("Yapay zekâ üretimi başarısız ({}): {}", generationId, t.toString());
            store.failAndRefund(generationId, snapshot.creditCost(), messageOf(t));
        }
    }

    /* ------------------------------------------------------------- yardımcılar */

    /**
     * Ürün görsellerini okur, küçültür ve <b>sağlayıcının deposuna yükler</b>.
     *
     * <p>🔴 <b>Neden kendi imzalı adresimizi göndermiyoruz:</b> o adres, ömrü
     * boyunca (7 gün) hesabın medya kapısını üçüncü bir tarafa açardı. Ayrıca
     * yerelde {@code localhost} dışarıdan erişilemez — araç geliştirme
     * ortamında hiç çalışmazdı.
     *
     * <p>🔴 <b>Neden data URI DEĞİL:</b> sağlayıcı gömülü baytları kabul
     * etmiyor ve bunu <b>söylemiyor</b> — 422 {@code no_media_generated} ile
     * "unsafe content" ima ediyor. Gerekçe ve ölçüm
     * {@link FalClient#uploadImage} başında.
     */
    private List<String> readProducts(List<UUID> mediaIds) {
        List<String> out = new ArrayList<>();
        int i = 0;
        for (UUID mediaId : mediaIds) {
            MediaObject media = mediaService.require(mediaId);
            byte[] jpeg;
            try (InputStream in = mediaService.openStream(media)) {
                jpeg = downscaleToJpeg(in.readAllBytes());
            } catch (FalClient.FalException e) {
                throw e;
            } catch (Exception e) {
                throw new FalClient.FalException("Ürün görseli okunamadı");
            }
            out.add(falClient.uploadImage(jpeg, "product-" + (++i) + ".jpg"));
        }
        return out;
    }

    /**
     * Uzun kenarı {@link #SEND_SIZE} olacak şekilde küçültüp JPEG'e çevirir.
     *
     * <p>⚠️ Alfa kanalı <b>düzleştirilir</b>: PNG'den gelen saydamlık JPEG
     * yazıcısını bozuyor ve görsel siyah bir kareye dönüşüyordu (aynı tuzak
     * {@code VisionProductDetector}'da da yaşandı). Saydam alan <b>beyaza</b>
     * düşer — ürün fotoğrafı için doğru varsayım.
     */
    private static byte[] downscaleToJpeg(byte[] source) throws Exception {
        BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(source));
        if (src == null) {
            throw new FalClient.FalException("Ürün görseli çözümlenemedi");
        }
        double scale = Math.min(1.0, (double) SEND_SIZE / Math.max(src.getWidth(), src.getHeight()));
        int nw = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int nh = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return bos.toByteArray();
    }

    /**
     * Sonucu sağlayıcıdan indirir.
     *
     * <p>⚠️ <b>Hemen</b> indirilir ve <b>kendi depomuza</b> yazılır: sağlayıcının
     * adresi geçicidir. Adresi saklayıp galeride göstermek, birkaç saat sonra
     * kırık görsellerle dolu bir galeri demekti.
     */
    private static byte[] download(String url) {
        try {
            HttpResponse<byte[]> res = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .timeout(Duration.ofSeconds(60))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                throw new FalClient.FalException("Sonuç görseli indirilemedi (" + res.statusCode() + ")");
            }
            byte[] body = res.body();
            if (body.length > MAX_RESULT_BYTES) {
                throw new FalClient.FalException("Sonuç görseli beklenenden büyük");
            }
            return body;
        } catch (FalClient.FalException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalClient.FalException("İndirme kesildi");
        } catch (Exception e) {
            throw new FalClient.FalException("Sonuç görseli indirilemedi");
        }
    }

    /**
     * ⚠️ Kullanıcıya yalnız <b>bizim</b> ürettiğimiz mesajlar gösterilir.
     * Sağlayıcının ham hata gövdesi istemi yankılayabiliyor ve iç ayrıntıyı
     * dışarı sızdırırdı; o gövde yalnız log'a yazılır ({@code FalClient}).
     */
    private static String messageOf(Throwable t) {
        return t instanceof FalClient.FalException ? t.getMessage() : "Üretim tamamlanamadı";
    }
}
