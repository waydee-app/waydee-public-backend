package com.waydee.social.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.storage.MediaUrlSigner;
import com.waydee.common.storage.StorageProperties;
import com.waydee.common.storage.StorageService;
import com.waydee.social.api.dto.SocialDtos.MediaResponse;
import com.waydee.social.application.MediaService;
import com.waydee.social.domain.MediaObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Tag(name = "Media", description = "Görsel yükleme ve servis etme")
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    /**
     * Depo adresinin ömrü — <b>7 gün</b>, yani AWS SigV4'ün izin verdiği azami süre
     * ({@code X-Amz-Expires} 604800'ü aşamaz).
     *
     * <p>🔴 Önce 6 saatti ve bu, farkında olmadan <b>tarayıcı önbelleğini
     * bozuyordu</b>: her çağrı yeni bir imza (dolayısıyla yeni bir URL) ürettiği
     * için, yönlendirme önbelleği dolduğu anda tarayıcı görseli <b>değişmemiş
     * olmasına rağmen</b> yeniden indiriyordu. Eski akıtma yolunda adres sabitti
     * ve görsel 7 gün önbellekte kalıyordu.
     *
     * <p>Süre, dış kapıdaki kendi HMAC imzamızın ömrüyle (7 gün, {@code
     * MediaUrlSigner}) bilinçli olarak <b>eşitlendi</b>: erişim penceresi zaten
     * o kadardı, depo adresinin daha kısa yaşaması güvenlik kazancı sağlamıyor,
     * yalnız bant genişliği harcıyordu.
     */
    private static final Duration DIRECT_TTL = Duration.ofDays(7);
    /**
     * Yönlendirmenin önbellek ömrü — depo adresinin ömrünün <b>altında</b> kalmalı,
     * yoksa önbellekten süresi dolmuş bir adres servis edilir ve görsel kırılır.
     */
    private static final Duration REDIRECT_CACHE = Duration.ofDays(6);

    private final MediaService mediaService;
    private final MediaUrlSigner urlSigner;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @Operation(summary = "Görsel yükle (JPEG/PNG/WebP/GIF, max 10 MB)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> upload(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.upload(principal.id(), file));
    }

    /*
     * 🔴 Video ve ses AYRI uçlarda, tek "upload"a bayrak eklenerek değil.
     * Sebep: her türün kendi tavanı ve kendi imza kontrolü var; tek uçta
     * birleştirmek, istemcinin gönderdiği bir parametreye göre 60 MB'lik
     * sınırın profil fotoğrafına da açılması riskini doğururdu.
     */
    @Operation(summary = "Video yükle (MP4/WebM, max 60 MB) — 3B mağaza televizyonu")
    @PostMapping(path = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> uploadVideo(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.uploadVideo(principal.id(), file));
    }

    @Operation(summary = "Ses yükle (MP3/OGG/WAV/WebM, max 15 MB) — mağaza müziği")
    @PostMapping(path = "/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> uploadAudio(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mediaService.uploadAudio(principal.id(), file));
    }

    @Operation(summary = "Yüklediğim görseller (medya kütüphanesi)")
    @GetMapping
    public List<MediaResponse> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return mediaService.listMine(principal.id());
    }

    /**
     * Görseller &lt;img&gt; ile yüklendiği için Authorization başlığı taşıyamaz;
     * yetki, bağlantıdaki kısa ömürlü HMAC imzasıyla doğrulanır.
     *
     * <p>🔴 <b>Baytlar artık backend'in içinden AKMAZ</b> (ölçek analizi bulgusu
     * K1). İmza doğrulandıktan sonra depoya ait kısa ömürlü imzalı adrese
     * <b>302</b> dönülür; tarayıcı görseli doğrudan S3'ten çeker.
     *
     * <p>Eskiden yanıt gövdesi {@code InputStreamResource} idi: indirme boyunca
     * bir Tomcat thread'i bloke oluyordu (20 görselli bir sayfa = 20 thread),
     * bant genişliği hem S3'ten hem kullanıcıya olmak üzere iki kez ödeniyordu
     * ve {@code cachePrivate} yüzünden CDN yanıtı <b>asla</b> önbelleğe
     * alamıyordu.
     *
     * <p>Yetki modeli değişmedi: kapıyı hâlâ bizim HMAC imzamız tutuyor,
     * doğrulanmadan hiçbir depo adresi üretilmiyor.
     */
    @Operation(summary = "Görseli getir (imzalı bağlantı)")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id,
                                 @RequestParam(name = "e", required = false) Long expiresAt,
                                 @RequestParam(name = "s", required = false) String signature,
                                 @RequestParam(name = "stream", required = false) Boolean stream) {
        if (expiresAt == null || !urlSigner.verify(id, expiresAt, signature)) {
            throw ApiException.forbidden("Geçersiz ya da süresi dolmuş medya bağlantısı");
        }
        MediaObject media = mediaService.require(id);

        /*
         * ═══════════════════════════════════════════════════════════════
         * 🔴 15 Ağu 2026 — `?stream=1`: WebGL İÇİN AYNI ORİJİNDEN AKIT
         *
         * <b>Yaşanmış hata:</b> 3B mağazada logo, raf fotoğrafları ve
         * televizyon videosu ÜRETİMDE hiç görünmüyordu; yerelde sorunsuzdu.
         *
         * <b>Kök neden (üretimde ölçüldü):</b> yukarıdaki 302 medyayı
         * <b>S3'e</b> yolluyor ve S3 <b>CORS başlığı DÖNDÜRMÜYOR</b>.
         * WebGL bir dokuyu ancak CORS onayı varsa kabul eder
         * ({@code TextureLoader} ve {@code VideoTexture} {@code crossOrigin}
         * kullanır) → doku sessizce boş kalır.
         * ⚠️ Düz {@code <img>}/{@code <video>} CORS istemez; bu yüzden aynı
         * dosya <b>stüdyoda oynuyor, 3B'de oynamıyordu</b> — belirtiyi bu
         * kadar kafa karıştırıcı yapan şey buydu.
         * ⚠️ <b>Yerelde ASLA görülemez</b>: MinIO gelen her {@code Origin}'i
         * yankılıyor, S3 yankılamıyor.
         *
         * <b>Neden akıtma?</b> Kalıcı çözüm S3 kovasına CORS tanımlamaktır
         * (bant genişliği ödemez). Ama o AWS tarafında bir iştir; bu bayrak
         * uygulamayı <b>bugün</b> çalışır kılar ve bedeli yalnız 3B sahnenin
         * ödediği birkaç dokudur — akışın geri kalanı (vitrin, avatar, feed)
         * 302'de kalır, yani K1 ölçek kazanımı korunur.
         *
         * ⚠️ İmza `stream` parametresini KAPSAMAZ ({@code id + e}) — dolayısıyla
         * bayrak yetkiyi zayıflatmaz, yalnız teslim yolunu seçer.
         * ═══════════════════════════════════════════════════════════════
         */
        boolean forceStream = Boolean.TRUE.equals(stream);

        if (!forceStream && storageProperties.directDeliveryEnabled()) {
            String target = storageService.presignedUrl(media.getObjectKey(), DIRECT_TTL);
            if (target != null) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(target))
                        // Yönlendirmenin kendisi önbelleğe alınabilir; anahtar,
                        // bağlantıdaki HMAC imzasıdır (tahmin edilemez), bu yüzden
                        // paylaşımlı önbellek güvenlidir. Süre, imzalı depo
                        // adresinin ömrünün ALTINDA tutulur — yoksa önbellekten
                        // süresi dolmuş bir adres servis edilir.
                        .cacheControl(CacheControl.maxAge(REDIRECT_CACHE).cachePublic())
                        .build();
            }
            // İmzalı adres üretilemedi → akıtmaya geri düş (medya kesilmesin).
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .contentLength(media.getSizeBytes())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .eTag(media.getId().toString())
                .body(new InputStreamResource(mediaService.openStream(media)));
    }
}
