package com.waydee.publicview.api;

import com.waydee.publicview.application.SeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * <b>Arama motoru uçları</b> — {@code robots.txt}, site haritaları ve bot
 * önizlemesi (prerender).
 *
 * <p>🔴 <b>Ziyaretçi geçişi (Turnstile) İSTENMEZ.</b> Diğer tüm public uçlar
 * {@code X-Waydee-Pass} ister; burada istemek <b>SEO'yu tamamen kapatırdı</b>:
 * Googlebot, Bingbot ve sosyal önizleme robotları Turnstile çözemez, JavaScript
 * çalıştırmaz ve başlık ekleyemez. Karşılığında bu uçlar <b>yalnız zaten
 * herkese açık</b> veriyi (açık hesapların adı, biyografisi, gönderi başlıkları)
 * ve <b>hiçbir kişisel iletişim bilgisini</b> döndürmez.
 *
 * <p>⚠️ Adresler {@code /api/v1/public/seo/**} altındadır çünkü güvenlik
 * yapılandırmasında bu önek zaten {@code permitAll}'dur; kök seviyedeki
 * {@code /robots.txt} ve {@code /sitemap.xml} adreslerine <b>nginx</b> yönlendirir
 * (bkz. {@code frontend/nginx.conf}). Böylece güvenlik kuralına dokunulmadı.
 */
@Tag(name = "SEO", description = "robots.txt, site haritaları ve bot önizlemesi")
@RestController
@RequestMapping("/api/v1/public/seo")
@RequiredArgsConstructor
public class SeoController {

    private final SeoService seo;

    @Operation(summary = "robots.txt")
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        return text(seo.robots(), MediaType.TEXT_PLAIN, Duration.ofHours(6));
    }

    @Operation(summary = "Site haritası indeksi")
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return text(seo.sitemapIndex(), MediaType.APPLICATION_XML, Duration.ofHours(6));
    }

    @Operation(summary = "Site haritası — kurumsal sayfalar")
    @GetMapping(value = "/sitemap-pages.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapPages() {
        return text(seo.sitemapPages(), MediaType.APPLICATION_XML, Duration.ofDays(1));
    }

    @Operation(summary = "Site haritası — kullanıcı vitrinleri")
    @GetMapping(value = "/sitemap-profiles-{page}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapProfiles(@PathVariable int page) {
        return text(seo.sitemapProfiles(page), MediaType.APPLICATION_XML, Duration.ofHours(6));
    }

    @Operation(summary = "Site haritası — gönderiler")
    @GetMapping(value = "/sitemap-posts-{page}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemapPosts(@PathVariable int page) {
        return text(seo.sitemapPosts(page), MediaType.APPLICATION_XML, Duration.ofHours(6));
    }

    /**
     * Bir uygulama adresinin bot için üretilmiş HTML karşılığı.
     *
     * <p>nginx yalnız <b>bot kullanıcı ajanlarını</b> buraya yönlendirir; gerçek
     * kullanıcı her zaman React uygulamasını alır.
     */
    @Operation(summary = "Bot önizlemesi (sunucuda üretilen HTML)")
    @GetMapping(value = "/render", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> render(@RequestParam(defaultValue = "/") String path) {
        SeoService.Rendered r = seo.render(path);
        return ResponseEntity.status(r.status())
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic())
                /*
                 * ⚠️ CSP burada BİLE BİLE gevşetilir. Spring Security tüm API
                 * yanıtlarına `default-src 'none'` yazıyor; bu bir JSON yanıtı
                 * için doğru, ama HTML belgesi için görseli ve satır içi JSON-LD
                 * betiğini engeller. Bu belge yalnız kendi görsellerini ve
                 * tek satır içi betiği taşır, o yüzden politika tam ona göre dar.
                 */
                .header("Content-Security-Policy",
                        "default-src 'none'; img-src 'self' https: data:; style-src 'unsafe-inline'; "
                                + "script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
                .body(r.html());
    }

    private static ResponseEntity<String> text(String body, MediaType type, Duration cache) {
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(cache).cachePublic())
                .body(body);
    }
}
