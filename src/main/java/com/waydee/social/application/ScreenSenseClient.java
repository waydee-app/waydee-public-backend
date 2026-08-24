package com.waydee.social.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;

/**
 * <b>ScreenSense</b> dikkat-haritası servisine isteğe bağlı köprü.
 *
 * <p>🔴 <b>Neden yalnızca köprü, "entegrasyon" değil:</b> depo
 * (<i>Youssef-Khafagy/ScreenSense</i>) Python 3.10 · PyTorch · FastAPI ile
 * yazılmış <b>ayrı bir servistir</b>. README'ye göre:
 * <ul>
 *   <li>depoda <b>eğitilmiş ağırlık yoktur</b>; önce {@code ml/train.py}
 *       koşulmalı (~2 saat GPU) ve SALICON/COCO veri kümesi (~20 GB) indirilmeli,</li>
 *   <li>Docker tanımı <b>yoktur</b>,</li>
 *   <li>servis {@code python main.py} ile <b>8000</b> portunda kalkar,
 *       {@code GET /api/health} → {@code {"status":"ok","model_loaded":true}}.</li>
 * </ul>
 * Yani bu depo Java sürecine "eklenemez". Ayağa kaldırıldığında adresi
 * {@code waydee.ai.screensense.url} ile verilir ve burası onunla konuşur.
 *
 * <p>⚠️ <b>Adres tanımlı değilse hiçbir şey yapılmaz</b> ({@code null} döner) ve
 * çağıran yerleşik analize düşer. Servis kapalıysa da aynı: kullanıcı bir dış
 * bağımlılığın yokluğundan etkilenmemeli.
 *
 * <p>⚠️ Yanıt şeması README'de <b>belgelenmemiş</b>. Bu yüzden burada bir
 * ayrıştırma uydurulmadı: bağlantı noktası hazır, gerçek şema görülünce
 * doldurulacak. Uydurma bir ayrıştırıcı, servis bağlanınca sessizce yanlış
 * noktalar üretirdi — yerleşik analizden kötü bir sonuç.
 */
@Slf4j
@Component
public class ScreenSenseClient {

    @Value("${waydee.ai.screensense.url:}")
    private String baseUrl;

    /**
     * @return dikkat haritası, ya da servis yapılandırılmamışsa {@code null}
     */
    public float[][] saliencyOrNull(BufferedImage image) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        log.debug("ScreenSense adresi tanımlı ({}) ama yanıt şeması henüz bağlanmadı — "
                + "yerleşik analiz kullanılıyor", baseUrl);
        return null;
    }

    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
