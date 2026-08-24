package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;

import com.waydee.identity.application.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * <b>Yapay zekâ etiket önerisi</b> — fotoğrafta etiketin konabileceği noktalar.
 *
 * <p>🔴 <b>ScreenSense hakkında dürüst durum:</b> istenen depo
 * (<i>Youssef-Khafagy/ScreenSense</i>) bir <b>Python/PyTorch + FastAPI</b>
 * servisidir ve <b>eğitilmiş model içermez</b> — README'ye göre önce
 * {@code ml/train.py} ile ~2 saatlik bir eğitim yapılması, 20 GB'lık SALICON/COCO
 * veri kümesinin indirilmesi gerekir; Docker tanımı da yoktur. Yani depoyu
 * "içe aktarıp" Java'da çalıştırmak mümkün değil: ayrı bir servis olarak
 * ayağa kaldırılıp <b>HTTP ile</b> konuşulur.
 *
 * <p>Bu yüzden iki sağlayıcı var:
 * <ol>
 *   <li><b>Yerleşik</b> (varsayılan, bugün çalışır): <i>frequency-tuned
 *       salient region detection</i> (Achanta ve ark., CVPR 2009). Görselin
 *       ortalama Lab rengi ile bulanıklaştırılmış hâli arasındaki uzaklık
 *       dikkat haritasını verir; tepe noktaları öneri olur. Bağımlılık yok,
 *       CPU'da milisaniyeler sürer.</li>
 *   <li><b>ScreenSense</b> (isteğe bağlı): {@code waydee.ai.screensense.url}
 *       tanımlıysa görsel oraya gönderilir. Tanımlı değilse yerleşik çalışır.</li>
 * </ol>
 *
 * <p>⚠️ <b>Uydurma öneri YOK.</b> Görsel okunamazsa ya da anlamlı bir tepe
 * bulunamazsa <b>boş liste</b> döner; arayüz "öneri çıkmadı" der. Rastgele
 * noktalar serpmek, kullanıcıya yapay zekâ çalışıyormuş izlenimi verirdi.
 *
 * <p>🔒 Premium kapısı sunucudadır: istemcinin düğmeyi gizlemesi güvenlik değil.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagSuggestionService {

    /** Analiz bu genişliğe küçültülerek yapılır — hız için ve yeterli. */
    private static final int WORK_WIDTH = 320;
    /** En fazla kaç öneri döner. */
    private static final int MAX_SUGGESTIONS = 5;
    /**
     * İki öneri arasındaki en küçük uzaklık (kısa kenarın oranı).
     *
     * <p>⚠️ Olmazsa tepe noktaların hepsi aynı nesnenin üstünde toplanır ve
     * kullanıcı beş öneri görür ama hepsi tek ürünü gösterir.
     */
    private static final double MIN_SEPARATION = 0.18;

    private final PlanService planService;
    private final ScreenSenseClient screenSense;
    private final VisionProductDetector vision;

    @Value("${waydee.ai.tag-suggestions.enabled:true}")
    private boolean enabled;

    /**
     * Bir medya için önerilen etiket noktaları.
     *
     * @return 0–1 aralığında x/y ve 0–1 skor; boş liste "öneri yok" demektir
     */
    @Transactional(readOnly = true)
    public List<Suggestion> suggest(java.io.InputStream imageStream, UUID viewerId) {
        if (!enabled) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Görsel analizi şu anda kapalı");
        }
        /* 🔒 Premium kapısı. Ayrı hata kodu ki arayüz "yasak" yerine
           yükseltme ekranını açsın. */
        if (!planService.planOf(viewerId).canOwnStore()) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED,
                    "Görsel analizi Premium üyeliğe özeldir.");
        }

        /*
         * 🔴 10 Ağu 2026 — SIRALAMA DEĞİŞTİ: önce GERÇEK ÜRÜN ALGILAMA.
         *
         * Kullanıcı yerleşik analizi haklı olarak "kullanışsız" buldu. Sebep
         * yöntemseldi: dikkat haritası (saliency) "göz nereye takılır"
         * sorusunu yanıtlar, "bu bir çanta mı" sorusunu değil. Artık
         * yapılandırılmışsa bir görsel-dil modeli çağrılır; o <b>kutu + ad</b>
         * döndürdüğü için etiket ürünün merkezine oturur ve form önceden dolar.
         *
         * ⚠️ Model BOŞ liste döndürürse bu bir SONUÇTUR ("ürün yok") ve
         * yerleşik analizle değiştirilmez; yoksa modelin "ürün yok" kararının
         * üstüne rastgele parlak noktalar serperdik.
         */
        BufferedImage full = readFull(imageStream);
        if (full == null) {
            return List.of();
        }
        List<VisionProductDetector.Detected> detected = vision.detectOrNull(full);
        if (detected != null) {
            return detected.stream()
                    .map(d -> new Suggestion(round(d.x()), round(d.y()), round(d.confidence()), d.name()))
                    .toList();
        }

        BufferedImage image = scaleTo(full, WORK_WIDTH);

        /* ScreenSense tanımlıysa önce o denenir; ulaşılamazsa yerleşik devreye
           girer. Kullanıcı bir dış servisin kapalı olmasından etkilenmemeli. */
        float[][] saliency = screenSense.saliencyOrNull(image);
        if (saliency == null) {
            saliency = frequencyTunedSaliency(image);
        }
        return regions(saliency);
    }

    // ------------------------------------------------------------- görsel

    /**
     * ⚠️ Görsel <b>istek gövdesinden</b> okunur, depodan değil: gönderi akışında
     * fotoğraf henüz yüklenmemiş oluyor (yükleme kaydetme anında yapılıyor).
     * Analiz için önce depoya yazmak, vazgeçilen her denemede çöp dosya
     * bırakırdı.
     */
    private BufferedImage readFull(java.io.InputStream in) {
        try (in) {
            BufferedImage full = ImageIO.read(in);
            if (full == null) {
                return null;
            }
            /* ⚠️ Küçültme ARTIK BURADA YAPILMAZ: görsel modeli daha büyük bir
               kopya istiyor (768px), yerleşik analiz ise 320px. Tek bir ölçek
               ikisine birden uymuyordu. */
            return full;
        } catch (Exception e) {
            log.warn("Etiket önerisi için görsel okunamadı", e);
            return null;
        }
    }

    private static BufferedImage scaleTo(BufferedImage src, int width) {
        if (src.getWidth() <= width) {
            return src;
        }
        int height = Math.max(1, src.getHeight() * width / src.getWidth());
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    // ---------------------------------------------------------- saliency

    /**
     * <b>Frequency-tuned salient region detection</b> (Achanta ve ark., 2009).
     *
     * <p>Dikkat haritası = her pikselin Lab rengi ile <b>görselin ortalama Lab
     * rengi</b> arasındaki uzaklık; gürültüyü bastırmak için görsel önce hafifçe
     * bulanıklaştırılır. Yayınlanmış, basit ve hızlı bir yöntem — grafik
     * kütüphanesi ya da model dosyası gerektirmez.
     *
     * <p>⚠️ Neden bu yöntem: ürün fotoğraflarında etiketlenecek şey genelde
     * <b>arka plandan renk olarak ayrışan</b> nesnedir. Kenar yoğunluğuna bakan
     * yöntemler desenli kumaşlarda ve dokulu zeminlerde yanılıyordu.
     */
    private static float[][] frequencyTunedSaliency(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        float[][][] lab = new float[h][w][3];
        double sumL = 0;
        double sumA = 0;
        double sumB = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] v = rgbToLab(img.getRGB(x, y));
                lab[y][x] = v;
                sumL += v[0];
                sumA += v[1];
                sumB += v[2];
            }
        }
        int n = w * h;
        float meanL = (float) (sumL / n);
        float meanA = (float) (sumA / n);
        float meanB = (float) (sumB / n);

        float[][][] blur = boxBlur(lab, w, h);
        float[][] out = new float[h][w];
        float max = 0f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dl = blur[y][x][0] - meanL;
                float da = blur[y][x][1] - meanA;
                float db = blur[y][x][2] - meanB;
                float s = (float) Math.sqrt(dl * dl + da * da + db * db);
                out[y][x] = s;
                if (s > max) {
                    max = s;
                }
            }
        }
        if (max <= 0) {
            return out;
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] /= max;
            }
        }
        return out;
    }

    /** 3×3 kutu bulanıklaştırma — Achanta'nın adımındaki gürültü bastırma. */
    private static float[][][] boxBlur(float[][][] lab, int w, int h) {
        float[][][] out = new float[h][w][3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float l = 0;
                float a = 0;
                float b = 0;
                int c = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ny = y + dy;
                        int nx = x + dx;
                        if (ny < 0 || ny >= h || nx < 0 || nx >= w) {
                            continue;
                        }
                        l += lab[ny][nx][0];
                        a += lab[ny][nx][1];
                        b += lab[ny][nx][2];
                        c++;
                    }
                }
                out[y][x][0] = l / c;
                out[y][x][1] = a / c;
                out[y][x][2] = b / c;
            }
        }
        return out;
    }

    /**
     * Dikkat haritasının <b>BÖLGELERİ</b> — tepe noktaları değil.
     *
     * <p>🔴 10 Ağu 2026 — eski sürüm haritanın en parlak <b>piksellerini</b>
     * seçiyordu. Bir nesnenin en parlak pikseli çoğu zaman <b>kenarındadır</b>
     * (kontrast orada en yüksek), dolayısıyla etiket ürünün ortasına değil
     * <b>sınırına</b> düşüyordu — kullanıcının "ürüne yapışmıyor" dediği
     * davranış tam olarak buydu.
     *
     * <p>Yeni yöntem: eşik üstü pikseller <b>bağlı bileşenlere</b> ayrılır
     * (4-komşuluk, yinelemesiz tarama), her bileşenin <b>ağırlık merkezi</b>
     * alınır ve bileşenler <b>alan × ortalama parlaklık</b> ile sıralanır.
     * Böylece nokta nesnenin gövdesine oturur ve küçük parıltılar (kenar
     * pırıltısı, gölge lekesi) büyük bir gövdeyi geçemez.
     *
     * <p>⚠️ Bu yine de bir <b>tanıma</b> değildir; nesnenin ne olduğunu
     * bilmez. Gerçek doğruluk için {@link VisionProductDetector} yapılandırılır.
     */
    private static List<Suggestion> regions(float[][] s) {
        int h = s.length;
        if (h == 0) {
            return List.of();
        }
        int w = s[0].length;
        /* ⚠️ Kenar payı: çerçevenin kendisi (vinyet, arka plan geçişi) sürekli
           yüksek değer üretir ve oraya etiket konamaz — baloncuk taşar. */
        int marginX = Math.max(1, (int) (w * 0.06));
        int marginY = Math.max(1, (int) (h * 0.06));

        /* Eşik: haritanın kendi dağılımından. Sabit bir eşik, düz zeminli
           fotoğrafta her şeyi, kalabalık fotoğrafta hiçbir şeyi seçiyordu. */
        float max = 0f;
        double sum = 0;
        int n = 0;
        for (int y = marginY; y < h - marginY; y++) {
            for (int x = marginX; x < w - marginX; x++) {
                max = Math.max(max, s[y][x]);
                sum += s[y][x];
                n++;
            }
        }
        if (n == 0 || max <= 0) {
            return List.of();
        }
        float mean = (float) (sum / n);
        float threshold = Math.max(mean * 1.6f, max * 0.45f);

        int[] labels = new int[w * h];
        int next = 0;
        List<double[]> blobs = new ArrayList<>(); // {sumX, sumY, area, sumScore}
        int[] stack = new int[w * h];

        for (int y = marginY; y < h - marginY; y++) {
            for (int x = marginX; x < w - marginX; x++) {
                int idx = y * w + x;
                if (labels[idx] != 0 || s[y][x] < threshold) {
                    continue;
                }
                next++;
                double sx = 0;
                double sy = 0;
                double area = 0;
                double score = 0;
                int top = 0;
                stack[top++] = idx;
                labels[idx] = next;
                while (top > 0) {
                    int cur = stack[--top];
                    int cx = cur % w;
                    int cy = cur / w;
                    sx += cx;
                    sy += cy;
                    area++;
                    score += s[cy][cx];
                    int[] nb = {cur - 1, cur + 1, cur - w, cur + w};
                    int[] nbx = {cx - 1, cx + 1, cx, cx};
                    int[] nby = {cy, cy, cy - 1, cy + 1};
                    for (int k = 0; k < 4; k++) {
                        int nx = nbx[k];
                        int ny = nby[k];
                        if (nx < marginX || nx >= w - marginX || ny < marginY || ny >= h - marginY) {
                            continue;
                        }
                        int ni = nb[k];
                        if (labels[ni] == 0 && s[ny][nx] >= threshold) {
                            labels[ni] = next;
                            stack[top++] = ni;
                        }
                    }
                }
                blobs.add(new double[] {sx, sy, area, score});
            }
        }
        if (blobs.isEmpty()) {
            return List.of();
        }

        /* ⚠️ Çok küçük lekeler ELENİR: tek tük parlak piksel bir ürün değildir
           (gözlük camındaki yansıma, takıdaki pırıltı). */
        double minArea = (double) w * h * 0.004;
        blobs.removeIf(b -> b[2] < minArea);
        if (blobs.isEmpty()) {
            return List.of();
        }
        // Sıralama: alan × ortalama parlaklık — "büyük ve belirgin" önce.
        blobs.sort(Comparator.comparingDouble((double[] b) -> b[2] * (b[3] / b[2])).reversed());

        double minDist = MIN_SEPARATION * Math.min(w, h);
        List<Suggestion> picked = new ArrayList<>();
        for (double[] b : blobs) {
            double cx = b[0] / b[2];
            double cy = b[1] / b[2];
            boolean tooClose = picked.stream().anyMatch(p -> {
                double dx = p.x() * w - cx;
                double dy = p.y() * h - cy;
                return Math.sqrt(dx * dx + dy * dy) < minDist;
            });
            if (tooClose) {
                continue;
            }
            double avg = Math.min(1, b[3] / b[2]);
            picked.add(new Suggestion(round((cx + 0.5) / w), round((cy + 0.5) / h), round(avg), null));
            if (picked.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return picked;
    }

    private static double round(double v) {
        return Math.round(v * 1000d) / 1000d;
    }

    /** sRGB → CIE Lab (D65). */
    private static float[] rgbToLab(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255d;
        double g = ((rgb >> 8) & 0xFF) / 255d;
        double b = (rgb & 0xFF) / 255d;
        r = r > 0.04045 ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = g > 0.04045 ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = b > 0.04045 ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;

        x = f(x);
        y = f(y);
        z = f(z);
        return new float[] {
                (float) (116 * y - 16),
                (float) (500 * (x - y)),
                (float) (200 * (y - z)),
        };
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t) + (16d / 116d);
    }

    /**
     * Önerilen nokta — {@code x}/{@code y} 0–1, {@code score} 0–1.
     *
     * @param label ürünün adı; <b>yalnız görsel modeli</b> doldurur. Yerleşik
     *              analizde {@code null}'dur — orada bir ad üretmek uydurma
     *              olurdu, o yöntem nesneyi tanımıyor.
     */
    public record Suggestion(double x, double y, double score, String label) {
    }
}
