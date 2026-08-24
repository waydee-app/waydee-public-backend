package com.waydee.common.logging;

import com.waydee.common.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * <b>Erişim logu — istek başına TEK satır</b> (20 Ağu 2026).
 *
 * <h3>🔴 Neden var</h3>
 * <p>Bu güne kadar backend'in <b>hiç erişim logu yoktu</b>. Ölçüldü: 30 günde
 * 1,16 MB log ve içinde tek bir HTTP satırı yok. Yani <i>"hangi uç yavaş",
 * "kim çağırdı", "ne 500 döndü"</i> sorularının <b>hiçbiri</b>
 * cevaplanamıyordu. Bir kullanıcı "hata aldım" dediğinde elde hiçbir iz yoktu.
 *
 * <h3>🔴 Neden TEK satır ve neden sonda</h3>
 * <p>İsteğin başında bir, sonunda bir satır yazmak log hacmini <b>ikiye
 * katlar</b> ve ikinci satır zaten birincinin bilgisini taşır. Tek satır
 * sonda yazılır çünkü <b>durum kodu ve süre</b> ancak o zaman bellidir.
 *
 * <h3>⚠️ Ne loglanmaz — bilinçli</h3>
 * <ul>
 *   <li><b>Gövde asla.</b> İçinde şifre, token, e-posta, gönderi metni olur.
 *       Bir kez loglanan sır, log saklandığı sürece sızmıştır.</li>
 *   <li><b>Başlıklar asla</b> — {@code Authorization} oradadır.</li>
 *   <li><b>Sorgu dizesi asla.</b> Arama terimleri ve jetonlar oraya düşer;
 *       yalnız <b>yol</b> tutulur.</li>
 *   <li><b>Sağlık kontrolleri ve statik dosyalar</b> — bkz. {@link #skip}.</li>
 * </ul>
 *
 * <h3>🔴 MDC: korelasyon</h3>
 * <p>Her isteğe bir {@code requestId} verilir, MDC'ye konur ve <b>o istek
 * boyunca yazılan HER log satırına</b> otomatik iliştirilir (yapısal JSON
 * biçimlendirici MDC'yi alan olarak yazar). Ayrıca yanıta
 * {@code X-Request-Id} olarak döner: kullanıcı hata ekranındaki kimliği
 * söyler, sen o tek kimlikle isteğin tüm izini çekersin.
 *
 * <p>⚠️ MDC {@code finally} içinde <b>mutlaka</b> temizlenir. Tomcat
 * iş parçacıklarını <b>havuzdan</b> verir; temizlenmezse bir sonraki isteğin
 * satırlarına <b>önceki kullanıcının kimliği</b> yapışır — sessiz ve tehlikeli
 * bir veri karışması.
 *
 * <h3>⚠️ Kullanıcı kimliği UUID olarak tutulur</h3>
 * <p>Kullanıcı talimatı (20 Ağu 2026). Hash değil UUID: destek talebinde
 * "bu kullanıcı" ile "bu log satırı"nı eşleştirmenin başka yolu yok.
 * ⚠️ Kimlik <b>oturumdan</b> okunur, istekten değil.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class RequestLogFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_IP = "ip";

    private final ClientIpResolver clientIpResolver;

    /**
     * Bu süreyi aşan istek <b>WARN</b> olur.
     *
     * <p>Ayarlanabilir: eşik bir <b>iş kararıdır</b> ve trafik büyüdükçe
     * değişir; kodu değiştirip yeniden dağıtmak gerekmesin.
     */
    @Value("${waydee.logging.slow-request-ms:500}")
    private long slowMs;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        /*
         * İstemcinin gönderdiği X-Request-Id KABUL EDİLMEZ, her zaman yenisi
         * üretilir. Kabul etmek, saldırganın kendi loglarını başkasının
         * isteğiyle aynı kimliğe yazdırmasına (log injection) izin verirdi.
         */
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();

        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_IP, clientIpResolver.resolve(request));
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            try {
                /*
                 * 🔴 KULLANICI KİMLİĞİ BURADA OKUNMAZ — okunamaz.
                 *
                 * İlk sürüm burada `SecurityContextHolder`a bakıyordu ve
                 * ÜRETİMDE ÖLÇÜLDÜ: `userId` hiçbir satıra girmedi. Sebep, bu
                 * filtrenin Spring Security zincirinin DIŞINDA olması: güvenlik
                 * zinciri bağlamı KENDİ `finally`sinde temizler ve o, bizim
                 * `finally`mizden ÖNCE koşar. Yani buraya geldiğimizde oturum
                 * bağlamı HER ZAMAN boştur.
                 *
                 * Kimliği artık `JwtAuthenticationFilter` doğrulandığı anda
                 * MDC'ye koyuyor; biz yalnız temizliyoruz (aşağıda).
                 *
                 * ⚠️ Ders: "sonda okursam her şeyi bilirim" sezgisi, araya
                 * giren bir çerçevenin kendi temizliği yüzünden YANLIŞTI.
                 */
                if (!skip(request)) {
                    long ms = (System.nanoTime() - started) / 1_000_000;
                    logLine(request, response, ms);
                }
            } finally {
                // Havuzdan gelen iş parçacığı temiz bırakılmalı (bkz. sınıf notu).
                MDC.remove(MDC_REQUEST_ID);
                MDC.remove(MDC_USER_ID);
                MDC.remove(MDC_IP);
            }
        }
    }

    /**
     * Seviye <b>sonuca</b> göre seçilir — böylece gürültü kendi kendini eler:
     * <ul>
     *   <li><b>ERROR</b> 5xx — bizim hatamız;</li>
     *   <li><b>WARN</b> 4xx ya da yavaş istek — bakılması gereken;</li>
     *   <li><b>INFO</b> gerisi.</li>
     * </ul>
     * Üretimde seviyeyi {@code WARN}'a çekmek, hacmi tek ayarla düşürür ve
     * <b>önemli olanı bırakır</b>. Bu, "her şeyi kapat" ile "her şeyi yaz"
     * arasındaki tek kullanışlı orta yol.
     */
    private void logLine(HttpServletRequest request, HttpServletResponse response, long ms) {
        int status = response.getStatus();
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (status >= 500) {
            log.error("{} {} -> {} ({} ms)", method, path, status, ms);
        } else if (status >= 400 || ms >= slowMs) {
            /*
             * 🔴 İşaretçi ASCII: "SLOW", "YAVAŞ" DEĞİL — ölçülerek öğrenildi.
             * Türkçe işaretçiyle CloudWatch metrik filtresi kurulmaya
             * çalışıldığında desen kabuk/CLI kodlamasında bozuldu
             * (`*YAVAŞ*` → `*YAVA?*`) ve filtre SESSİZCE hiçbir şey saymadı.
             * Log satırı burada bir MAKİNE arayüzüdür; insan için olan kısım
             * zaten yol ve süredir.
             */
            log.warn("{} {} -> {} ({} ms){}", method, path, status, ms,
                    ms >= slowMs ? " SLOW" : "");
        } else {
            log.info("{} {} -> {} ({} ms)", method, path, status, ms);
        }
    }

    /**
     * <b>Loglanmayacak yollar.</b>
     *
     * <p>🔴 Ölçüldü ve bu eleme şart: frontend'in nginx logunun neredeyse
     * tamamı <b>ELB sağlık kontrolüydü</b> (30 sn'de bir, iki AZ'den). Aynı
     * gürültüyü backend'e taşımanın hiçbir faydası yok — sağlık kontrolünün
     * başarısı zaten ECS'in kendi ölçümüdür.
     *
     * <p>⚠️ Statik varlıklar da elenir: bir sayfa açılışı onlarca dosya
     * çeker ve hepsi aynı şeyi söyler.
     */
    private boolean skip(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.equals("/favicon.ico")
                || path.startsWith("/assets/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
