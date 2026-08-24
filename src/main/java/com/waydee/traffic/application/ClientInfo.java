package com.waydee.traffic.application;

import com.waydee.common.net.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * İstekten istemci bilgisi çıkarır: IP, ülke, cihaz, tarayıcı, işletim sistemi.
 *
 * Ülke: harici bir servise çıkılmaz (gizlilik + bağımlılık). Üretimde CDN/LB'nin
 * eklediği ülke başlığı okunur (`CF-IPCountry`, `X-Country-Code`, `X-Geo-Country`);
 * yoksa özel/yerel adresler "Yerel ağ" sayılır, kalanı "Bilinmiyor" olur.
 */
@Component
@RequiredArgsConstructor
public class ClientInfo {

    /**
     * 🔴 17 Ağu 2026 — IP artık zincirin <b>sağından</b> okunuyor.
     * Eskiden en soldaki {@code X-Forwarded-For} girdisi alınıyordu ve o girdiyi
     * istemci yazdığı için giriş geçmişine / denetim kaydına <b>istenen IP</b>
     * yazdırılabiliyordu. Gerekçe: {@link ClientIpResolver}.
     */
    private final ClientIpResolver clientIpResolver;

    public String ip(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    public String country(HttpServletRequest request) {
        for (String header : new String[]{"CF-IPCountry", "X-Country-Code", "X-Geo-Country", "X-AppEngine-Country"}) {
            // 🔒 Bu başlıkları CDN/LB ekler. Güvenilir bir vekilin arkasında
            // değilsek istemcinin kendi yazdığı değerdir — okunmaz.
            String value = clientIpResolver.trustedHeader(request, header);
            if (value != null && !value.isBlank() && !"XX".equalsIgnoreCase(value)) {
                return countryName(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        String ip = ip(request);
        return isLocal(ip) ? "Yerel ağ" : "Bilinmiyor";
    }

    public String device(String userAgent) {
        if (userAgent == null) {
            return "Bilinmiyor";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "Tablet";
        }
        if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobil";
        }
        return "Masaüstü";
    }

    public String browser(String userAgent) {
        if (userAgent == null) {
            return "Bilinmiyor";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("chrome/") && !ua.contains("chromium")) return "Chrome";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("safari/")) return "Safari";
        return "Diğer";
    }

    public String os(String userAgent) {
        if (userAgent == null) {
            return "Bilinmiyor";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        if (ua.contains("mac os")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        return "Diğer";
    }

    private boolean isLocal(String ip) {
        if (ip == null) {
            return false;
        }
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.2") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.") || "::1".equals(ip) || ip.startsWith("0:0:0:0");
    }

    /** Sık görülen ülke kodları için Türkçe ad; bilinmeyen kod olduğu gibi döner. */
    private String countryName(String code) {
        return switch (code) {
            case "TR" -> "Türkiye";
            case "DE" -> "Almanya";
            case "US" -> "ABD";
            case "GB" -> "Birleşik Krallık";
            case "NL" -> "Hollanda";
            case "FR" -> "Fransa";
            case "AZ" -> "Azerbaycan";
            case "RU" -> "Rusya";
            case "SA" -> "Suudi Arabistan";
            case "AE" -> "BAE";
            case "IT" -> "İtalya";
            case "ES" -> "İspanya";
            case "AT" -> "Avusturya";
            case "BE" -> "Belçika";
            case "CH" -> "İsviçre";
            case "SE" -> "İsveç";
            case "CA" -> "Kanada";
            case "AU" -> "Avustralya";
            case "IN" -> "Hindistan";
            case "CY" -> "Kıbrıs";
            case "BG" -> "Bulgaristan";
            case "GR" -> "Yunanistan";
            case "UA" -> "Ukrayna";
            case "PL" -> "Polonya";
            default -> code;
        };
    }
}
