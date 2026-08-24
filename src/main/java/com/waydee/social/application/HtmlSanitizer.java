package com.waydee.social.application;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kullanıcı HTML'i için sunucu tarafı temizleyici.
 *
 * <p>Bu, savunmanın <b>ilk</b> katmanıdır; ikinci katman istemcideki
 * <code>sandbox</code> iframe'dir (<b>allow-same-origin YOK</b>, script yok).
 * İkisi birlikte: XSS ile oturum çalınamaz, form ile kimlik avı yapılamaz,
 * dış kaynağa veri sızdırılamaz.
 *
 * <p>Yaklaşım: tehlikeli etiketleri içerikleriyle birlikte kaldır, olay
 * işleyicilerini (<code>on*</code>) ve <code>javascript:</code> bağlantılarını sil.
 * Kalanı düz HTML+CSS'tir.
 */
@Component
public class HtmlSanitizer {

    public static final int MAX_LENGTH = 20_000;

    /** İçerikleriyle birlikte tamamen silinen etiketler. */
    private static final Pattern DANGEROUS_BLOCKS = Pattern.compile(
            "(?is)<\\s*(script|iframe|object|embed|applet|form|frameset|frame|noscript|template)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    /** Kapanışı olmayan tehlikeli/tekil etiketler. */
    private static final Pattern DANGEROUS_SINGLE = Pattern.compile(
            "(?is)<\\s*/?\\s*(script|iframe|object|embed|applet|form|frameset|frame|noscript|template|base|meta\\s+http-equiv)\\b[^>]*>");
    /** on* olay işleyicileri: onclick="..." / onload='...' / onerror=... */
    private static final Pattern EVENT_HANDLERS = Pattern.compile(
            "(?is)\\son[a-z-]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    /** javascript:, vbscript:, data:text/html gibi çalıştırılabilir URL şemaları. */
    private static final Pattern SCRIPT_URLS = Pattern.compile(
            "(?is)(href|src|action|formaction|xlink:href)\\s*=\\s*(\"|')?\\s*(javascript|vbscript|data\\s*:\\s*text/html)[^\"'>\\s]*(\"|')?");
    /** CSS içindeki expression()/url(javascript:) kalıntıları. */
    private static final Pattern CSS_EXPRESSION = Pattern.compile("(?is)expression\\s*\\(|javascript\\s*:");

    /**
     * @return temizlenmiş HTML; boş/whitespace ise null.
     * @throws com.waydee.common.error.ApiException uzunluk sınırı aşılırsa
     */
    public String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() > MAX_LENGTH) {
            throw com.waydee.common.error.ApiException.badRequest(
                    "HTML en fazla %d karakter olabilir".formatted(MAX_LENGTH));
        }

        String html = raw;
        // Tehlikeli blokları içerikleriyle birlikte sil (iç içe olabilir → yinele).
        for (int i = 0; i < 4; i++) {
            String before = html;
            html = DANGEROUS_BLOCKS.matcher(html).replaceAll("");
            if (before.equals(html)) {
                break;
            }
        }
        html = DANGEROUS_SINGLE.matcher(html).replaceAll("");
        html = EVENT_HANDLERS.matcher(html).replaceAll("");
        html = SCRIPT_URLS.matcher(html).replaceAll(Matcher.quoteReplacement("href=\"#\""));
        html = CSS_EXPRESSION.matcher(html).replaceAll("");

        String trimmed = html.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** `sema:` benzeri bir on ek (ardindan rakam gelmeyen) — `host:port` haric. */
    private static final java.util.regex.Pattern SCHEME_LIKE =
            java.util.regex.Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:(?![0-9])");

    /**
     * Profil/site bağlantısı için varsayılan üst sınır.
     *
     * <p>Bu değer ilgili sütunlarla hizalıdır (`profiles.website VARCHAR(200)`,
     * `post_social_links.value VARCHAR(300)`); kısa, elle yazılan adresler.
     */
    private static final int DEFAULT_MAX_URL = 200;

    /** Web sitesi bağlantısını doğrular: yalnız http/https, en fazla 200 karakter. */
    public String normalizeWebsite(String raw) {
        return normalizeWebsite(raw, DEFAULT_MAX_URL);
    }

    /**
     * 🔴 <b>10 Ağustos 2026 — ÜST SINIR ARTIK ÇAĞIRANIN.</b>
     *
     * <p><b>Yaşanan hata:</b> ürün etiketi bağlantıları da bu yöntemden
     * geçiyordu ve <b>200 karakterde</b> reddediliyordu. Oysa
     * `post_tags.product_url` sütunu <b>VARCHAR(500)</b>'dür ve gerçek
     * pazaryeri adresleri (Trendyol/Amazon/Hepsiburada, izleme parametreleriyle)
     * 200 karakteri <b>rutin olarak</b> aşar. Kullanıcı gerçek bir ürün linki
     * yapıştırdığında <b>400</b> alıyor, düzenleme ekranı bu hatayı yutuyor ve
     * ekranda <b>hiçbir şey olmuyordu</b> — bildirilen "etiket kaydolmuyor"
     * hatasının kaynağı buydu.
     *
     * <p>⚠️ Sınır <b>gevşetilmedi, çağırana bırakıldı</b>: her alan kendi
     * sütununun genişliğini bilir. Hepsini 500'e çekmek, 200'lük sütunlara
     * sığmayan değerlerin veritabanı hatasıyla patlamasına yol açardı.
     *
     * @param maxLength çağıran alanın sütun genişliği
     */
    public String normalizeWebsite(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String url = raw.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            /*
             * 🔴 ŞEMASI OLAN AMA http/https OLMAYAN DEĞER REDDEDİLİR.
             *
             * Eskiden buraya körlemesine "https://" ekleniyordu; sonuç
             * `javascript:alert(1)` → `https://javascript:alert(1)` idi ve
             * kayıt KABUL EDİLİYORDU (ölçüldü: 201). Adres tarayıcıda
             * çalışmasa da "yalnız http/https" sözleşmesi delinmiş oluyordu
             * ve profilde bozuk bir bağlantı duruyordu.
             *
             * ⚠️ `host:port` bunun dışındadır: `example.com:8080` da iki
             * nokta içerir. Ayrım, iki noktadan SONRAKİ karakterin rakam
             * olup olmamasıdır — şema adından sonra rakam gelmez.
             */
            if (SCHEME_LIKE.matcher(url).find()) {
                throw com.waydee.common.error.ApiException.badRequest(
                        "Yalnızca http/https adresleri eklenebilir");
            }
            url = "https://" + url;
            lower = url.toLowerCase(Locale.ROOT);
        }
        if (url.length() > maxLength) {
            throw com.waydee.common.error.ApiException.badRequest(
                    "Adres en fazla " + maxLength + " karakter olabilir");
        }
        return url;
    }
}
