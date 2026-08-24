package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Auto Fetch Data</b> — ürün adresinden ad, görsel ve fiyat çıkarır.
 *
 * <p>Referansın "Add Tag" penceresindeki işaretli kutunun karşılığı. Kullanıcı
 * adresi yapıştırır, alanlar kendiliğinden dolar.
 *
 * <h3>🔒 Neden bu kadar çok kapı var</h3>
 * Bu uç, <b>kullanıcının verdiği adrese sunucunun kendisi istek atar</b>. Kapısız
 * bırakılırsa klasik <b>SSRF</b> açığıdır: saldırgan {@code http://169.254.169.254}
 * (bulut metadata) ya da {@code http://localhost:8091} yazıp sunucunun iç ağını
 * bize taratabilir. Kapılar:
 * <ul>
 *   <li><b>Yalnız http/https</b> — {@code file:}, {@code gopher:} vb. reddedilir.</li>
 *   <li><b>Özel/döngü/bağlantı-yerel IP'ler reddedilir</b> — adres önce çözülür,
 *       sonra kontrol edilir (isim çözümü sonrası kontrol şart: {@code evil.com}
 *       pekâlâ {@code 127.0.0.1}'e çözülebilir).</li>
 *   <li><b>Yönlendirme takip EDİLMEZ</b> — aksi halde ilk adres masum, ikinci
 *       adres iç ağ olabilirdi.</li>
 *   <li><b>Zaman aşımı 4 sn, gövde 512 KB ile sınırlı</b> — yavaş/dev bir sayfa
 *       isteği asamaz.</li>
 * </ul>
 *
 * <p>⚠️ Başarısızlık <b>hata değildir</b>: alanlar boş döner, kullanıcı elle
 * doldurur. Etiket eklemeyi bir üçüncü tarafın erişilebilirliğine bağlamak
 * akışı kırardı.
 */
@Slf4j
@Service
public class LinkPreviewService {

    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final int MAX_BODY = 512 * 1024;

    private static final Pattern META = Pattern.compile(
            "<meta[^>]+(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"'][^>]+content\\s*=\\s*[\"']([^\"']*)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern META_REVERSE = Pattern.compile(
            "<meta[^>]+content\\s*=\\s*[\"']([^\"']*)[\"'][^>]+(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            // 🔒 Yönlendirme takip edilmez (SSRF kapısı).
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public Preview fetch(String rawUrl) {
        URI uri = safeUri(rawUrl);
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "WaydeeBot/1.0 (+https://waydee.com)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() >= 400) {
                return Preview.empty();
            }
            String html = readLimited(res.body());
            return parse(html);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // ⚠️ Sessiz düşülür: hedef site erişilemezse etiket yine elle eklenir.
            log.debug("Bağlantı önizlemesi alınamadı: {}", e.toString());
            return Preview.empty();
        }
    }

    /** 🔒 Şema + çözülen IP kontrolü. Geçersizse istek HİÇ atılmaz. */
    private static URI safeUri(String rawUrl) {
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Adres geçersiz");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Yalnız http/https adresleri okunabilir");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Adres geçersiz");
        }
        try {
            InetAddress[] resolved = InetAddress.getAllByName(host);
            if (resolved.length == 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Adres çözümlenemedi");
            }
            for (InetAddress addr : resolved) {
                if (isBlockedAddress(addr)) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bu adres okunamaz");
                }
            }
        } catch (UnknownHostException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Adres çözümlenemedi");
        }
        return uri;
    }

    /**
     * 🔒 17 Ağu 2026 — SSRF eleği <b>genişletildi</b>.
     *
     * <p>Java'nın hazır yüklemleri tek başına yetmiyordu; üç boşluk vardı ve
     * üçü de gerçek bir bulut kurulumunda iç ağa çıkıyor:
     * <ul>
     *   <li><b>IPv6 benzersiz-yerel</b> ({@code fc00::/7}) — {@code isSiteLocalAddress()}
     *       yalnız IPv4'ün 10/172.16/192.168 bloklarını ve <b>kullanımdan
     *       kaldırılmış</b> {@code fec0::/10}'u bilir. {@code fd00::…} elekten
     *       geçiyordu.</li>
     *   <li><b>CGNAT</b> ({@code 100.64.0.0/10}) — ECS/EKS ve pek çok sağlayıcı
     *       iç trafiği bu blokta taşır; "özel" sayılmadığı için geçiyordu.</li>
     *   <li><b>IPv4-eşlemeli IPv6</b> ({@code ::ffff:127.0.0.1}) — sarmalanmış
     *       hâlde bazı yüklemler yanlış cevap verebilir; adres açıkça
     *       IPv4'e indirgenip yeniden bakılır.</li>
     * </ul>
     *
     * <p>⚠️ <b>Bilinen kalan risk (DNS rebinding):</b> burada çözülen adres ile
     * {@code HttpClient}'ın bağlanırken yeniden çözdüğü adres teoride farklı
     * olabilir (TOCTOU). Tam çözüm, bağlantıyı doğrulanmış IP'ye <b>pinlemek</b>
     * için özel bir soket fabrikası ister; {@code java.net.http} bunu doğrudan
     * desteklemiyor. Etki sınırlı: yönlendirme takip edilmiyor, gövde yalnız
     * meta etiketler için ayrıştırılıyor ve <b>hiçbir yanıt gövdesi kullanıcıya
     * dönmüyor</b> — dönen tek şey başlık/görsel/fiyat alanları.
     */
    private static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 16) {
            // fc00::/7 — IPv6 benzersiz-yerel adresler (fd00::… dahil).
            if ((raw[0] & 0xFE) == 0xFC) {
                return true;
            }
            // ::ffff:a.b.c.d — eşlemeli IPv4'ü açıp aynı kurallardan geçir.
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF) {
                return isBlockedIpv4(raw[12], raw[13]);
            }
            return false;
        }
        return raw.length == 4 && isBlockedIpv4(raw[0], raw[1]);
    }

    /** 100.64.0.0/10 (CGNAT) ve 169.254.0.0/16 (bulut metadata) — ilk iki bayt yeter. */
    private static boolean isBlockedIpv4(byte first, byte second) {
        int a = first & 0xFF;
        int b = second & 0xFF;
        if (a == 100 && b >= 64 && b <= 127) {
            return true;
        }
        return a == 169 && b == 254;
    }

    /** Gövde tavanı: dev bir sayfa belleği doldurmasın. */
    private static String readLimited(InputStream in) throws IOException {
        byte[] buf = in.readNBytes(MAX_BODY);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static Preview parse(String html) {
        String title = meta(html, "og:title");
        if (title == null) title = meta(html, "twitter:title");
        if (title == null) {
            Matcher m = TITLE.matcher(html);
            if (m.find()) title = m.group(1).trim();
        }
        String image = meta(html, "og:image");
        if (image == null) image = meta(html, "twitter:image");

        // Fiyat: Open Graph ürün etiketleri en yaygın kaynak.
        String price = meta(html, "product:price:amount");
        if (price == null) price = meta(html, "og:price:amount");
        String currency = meta(html, "product:price:currency");
        if (currency == null) currency = meta(html, "og:price:currency");

        return new Preview(clean(title), clean(image), toDecimal(price), clean(currency));
    }

    private static String meta(String html, String key) {
        Matcher m = META.matcher(html);
        while (m.find()) {
            if (m.group(1).equalsIgnoreCase(key)) return m.group(2);
        }
        // Bazı siteler `content` özniteliğini ÖNCE yazar; ikinci desen onu yakalar.
        m = META_REVERSE.matcher(html);
        while (m.find()) {
            if (m.group(2).equalsIgnoreCase(key)) return m.group(1);
        }
        return null;
    }

    private static String clean(String v) {
        if (v == null) return null;
        String s = v.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > 140 ? s.substring(0, 140) : s;
    }

    private static BigDecimal toDecimal(String v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", ".").replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Alanların hepsi null olabilir — kullanıcı elle doldurur. */
    public record Preview(String title, String imageUrl, BigDecimal price, String currency) {
        static Preview empty() {
            return new Preview(null, null, null, null);
        }
    }
}
