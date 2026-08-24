package com.waydee.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waydee.payment.application.CheckoutService;
import com.waydee.payment.application.PolarProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Polar webhook alıcısı (12 Ağustos 2026 — LemonSqueezy alıcısının yerine).
 *
 * <p><b>Güvenlik:</b> uç kimliksizdir (sağlayıcı JWT taşıyamaz) ama
 * <b>imzasızdır kabul edilmez</b>. Polar <b>Standard Webhooks</b> kullanır:
 * <pre>
 *   imzalanan içerik = "{webhook-id}.{webhook-timestamp}.{ham gövde}"
 *   imza            = Base64( HMAC-SHA256( gizli anahtar, imzalanan içerik ) )
 *   başlık          = "v1,&lt;imza&gt;"   (anahtar döndürülürken boşlukla ayrılmış birden fazla olabilir)
 * </pre>
 * İmza doğrulanmadan gövdeye BAKILMAZ — aksi halde herkes "ödendi" diyerek
 * bedava üyelik alabilirdi.
 *
 * <p>🔴 <b>LemonSqueezy'den üç fark</b> (hepsi sessiz kırılma sebebi):
 * <ol>
 *   <li>başlık {@code X-Signature} değil <b>{@code webhook-signature}</b>,</li>
 *   <li>imza hex değil <b>base64</b> ve <b>{@code v1,} önekli</b>,</li>
 *   <li>imzalanan şey gövde değil, <b>id.timestamp.gövde</b> üçlüsü.</li>
 * </ol>
 *
 * <p>⚠️ İmza <b>ham gövde</b> üzerinden hesaplanır; Jackson'ın ayrıştırıp
 * yeniden ürettiği JSON boşluk/sıra farkı yüzünden farklı bir hash verirdi. Bu
 * yüzden gövde {@code String} olarak alınır.
 *
 * <p>Yanıt her zaman 2xx'tir (imza hatası hariç): sağlayıcı 5xx görürse aynı
 * olayı saatlerce tekrar dener. İşleme hatası kayıt altına alınır, tekrar
 * istenmez — işlem zaten idempotenttir.
 */
@Slf4j
@Tag(name = "Payments", description = "Ödeme sağlayıcısı bildirimleri")
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    /**
     * Eski bir bildirimin tekrar oynatılmasına karşı pencere.
     *
     * <p>Standard Webhooks 5 dakika önerir; sunucu saati birkaç saniye kaysa
     * bile meşru bildirimler reddedilmesin diye 10 dakika kullanılıyor.
     * Tamamlama zaten idempotent olduğu için bu pencere ikinci savunma hattıdır.
     */
    private static final Duration TOLERANCE = Duration.ofMinutes(10);

    private final CheckoutService checkoutService;
    private final PolarProperties properties;
    private final ObjectMapper objectMapper;
    /** 🔒 17 Ağu 2026 — denetim kaydına yazılan IP artık sahte X-Forwarded-For ile ezilemez. */
    private final com.waydee.common.net.ClientIpResolver clientIpResolver;

    @PostMapping("/polar")
    public ResponseEntity<String> polar(@RequestBody String rawBody,
                                        @RequestHeader(value = "webhook-id", required = false) String webhookId,
                                        @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "webhook-signature", required = false) String signature,
                                        HttpServletRequest http) {
        if (!verify(rawBody, webhookId, timestamp, signature)) {
            log.warn("Polar webhook imzası geçersiz ({})", clientIpResolver.resolve(http));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("type").asText("");
            JsonNode data = root.path("data");

            /* Yalnız ÖDENMİŞ sipariş plan yükseltir. `order.created` ödeme
               tamamlanmadan da düşebilir; onu plana çevirmek ödenmemiş üyelik
               dağıtmak olurdu (LemonSqueezy'de aynı tuzak için `status=paid`
               kontrolü vardı — Polar'da ayrı bir olay adı olarak geliyor). */
            if (!"order.paid".equals(event)) {
                log.info("Polar olayı yok sayıldı: {}", event);
                return ResponseEntity.ok("ignored");
            }

            String orderId = data.path("id").asText(null);
            String reservation = reservationOf(data);
            if (reservation == null || reservation.isBlank()) {
                log.warn("Polar webhook'unda rezervasyon kimliği yok (sipariş {})", orderId);
                return ResponseEntity.ok("no reservation");
            }

            checkoutService.completePaid(UUID.fromString(reservation), orderId, clientIpResolver.resolve(http));
            return ResponseEntity.ok("ok");
        } catch (Exception ex) {
            // 2xx dönülür: tekrar denemek aynı hatayı üretir, işlem idempotenttir.
            log.error("Polar webhook işlenemedi: {}", ex.getMessage());
            return ResponseEntity.ok("error logged");
        }
    }

    /**
     * Rezervasyon kimliğini <b>üç yerde</b> arar.
     *
     * <p>🔴 İlk ödemede metadata siparişin üzerindedir. <b>Yenileme
     * siparişlerinde</b> ise sipariş, metadata'yı aboneliğinden devralır ve
     * Polar bunu her zaman siparişin köküne kopyalamayabilir. Tek yere bakmak,
     * ikinci ayın ödemesinin sahipsiz kalması ve üyeliğin sessizce düşmesi
     * demekti.
     */
    private static String reservationOf(JsonNode data) {
        for (JsonNode holder : new JsonNode[]{
                data,
                data.path("subscription"),
                data.path("checkout")}) {
            String value = holder.path("metadata").path("reservation").asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean verify(String rawBody, String webhookId, String timestamp, String signature) {
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            // Fail-closed: gizli anahtar yoksa hiçbir bildirim kabul edilmez.
            log.error("POLAR_WEBHOOK_SECRET tanımlı değil — webhook reddedildi");
            return false;
        }
        if (webhookId == null || timestamp == null || signature == null
                || webhookId.isBlank() || timestamp.isBlank() || signature.isBlank()) {
            return false;
        }
        if (!withinTolerance(timestamp)) {
            log.warn("Polar webhook zaman damgası pencere dışında: {}", timestamp);
            return false;
        }

        String signedContent = webhookId + "." + timestamp + "." + rawBody;
        /* 🔴 Anahtar iki biçimde denenir. Standard Webhooks sırrın base64
           çözülmesini şart koşar, Polar ise sırrı ham dize olarak verir ve
           kütüphaneler onu önce base64'leyip sonra çözer — yani pratikte HMAC
           anahtarı sırrın HAM baytlarıdır. İkisini de denemek, yanlış tahminin
           bedelinin "üretimde hiçbir ödeme işlenmiyor" olduğu bir yerde ucuz
           bir sigortadır. */
        String[] expected = {
                // ① sırrın ham baytları (Polar sırrı `whsec_…` önekiyle verir)
                sign(secret.getBytes(StandardCharsets.UTF_8), signedContent),
                // ② sır base64 ise çözülmüş hâli
                decodedKeySignature(secret, signedContent),
                // ③ Standard Webhooks'un kitabına göre: `whsec_` atılıp base64 çözülür
                decodedKeySignature(stripPrefix(secret), signedContent),
        };

        for (String candidate : signature.trim().split("\\s+")) {
            // "v1,<imza>" — şema öneki atılır; ileride v2 gelirse eşleşmez, doğrusu da budur.
            String value = candidate.startsWith("v1,") ? candidate.substring(3) : candidate;
            for (String e : expected) {
                if (matches(value, e)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stripPrefix(String secret) {
        return secret.startsWith("whsec_") ? secret.substring("whsec_".length()) : secret;
    }

    /** Sır geçerli base64 ise onun çözülmüş hâli de anahtar adayıdır. */
    private static String decodedKeySignature(String secret, String signedContent) {
        try {
            return sign(Base64.getDecoder().decode(secret), signedContent);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String sign(byte[] key, String signedContent) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            log.error("Webhook imzası hesaplanamadı: {}", ex.getMessage());
            return null;
        }
    }

    /** Zamanlama-güvenli karşılaştırma — imza baytlarını sızdırmamak için. */
    private static boolean matches(String actual, String expected) {
        return expected != null && MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean withinTolerance(String timestamp) {
        try {
            Instant sentAt = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
            return Duration.between(sentAt, Instant.now()).abs().compareTo(TOLERANCE) <= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
