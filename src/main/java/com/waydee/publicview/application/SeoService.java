package com.waydee.publicview.application;

import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView;
import com.waydee.identity.application.SocialLinkService;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserStatus;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.ProfilePostController;
import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostSocialLink;
import com.waydee.social.domain.PostTag;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.PostSocialLinkRepository;
import com.waydee.social.infrastructure.PostTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>Arama motoru katmanı</b> — site haritaları ve <b>sunucuda üretilen HTML</b>.
 *
 * <p>🔴 <b>Neden gerekli:</b> Waydee tek sayfalık bir React uygulamasıdır;
 * sunucudan gelen belge boş bir {@code <div id="root">}'tur. Bu, arama
 * motorunun gördüğü ilk (ve çoğu tarayıcıda tek) hâldir. Google JavaScript
 * çalıştırabilir ama <b>ikinci bir sırada</b>, günler sonra ve bütçeyle;
 * <b>WhatsApp, X, LinkedIn, Slack ve Bing önizlemeleri JavaScript hiç
 * çalıştırmaz</b>. Yani bugüne kadar bir kullanıcı profili paylaşıldığında
 * karşı tarafta <b>"WAYDEE · Dünyada Yerini Al"</b> yazan, kişiyle hiç ilgisi
 * olmayan bir kart çıkıyordu ve kişinin adı, ürünleri, bağlantıları hiçbir
 * yerde indekslenmiyordu.
 *
 * <p><b>Çözüm iki katmanlıdır:</b>
 * <ol>
 *   <li><b>Sunucu HTML'i</b> (bu sınıf): tarayıcı olmayan istemciye (bot) tam
 *       başlık, açıklama, kapak görseli, kanonik adres, <b>görünür metin</b> ve
 *       <b>JSON-LD</b> ile dolu gerçek bir belge döner.</li>
 *   <li><b>İstemci meta'sı</b> (arayüzdeki {@code useSeo}): gerçek kullanıcıda
 *       aynı değerleri gezinme sırasında günceller.</li>
 * </ol>
 *
 * <p>⚠️ Bu <b>cloaking değildir</b>: bota gösterilen metin, kullanıcının
 * gördüğü metnin aynısıdır (ad, biyografi, gönderi adları, ürün adları).
 * Google'ın yasakladığı şey <b>farklı içerik</b> göstermektir, aynı içeriği
 * farklı biçimde sunmak değil (dynamic rendering).
 *
 * <p>🔒 Yalnız <b>indekslenebilir</b> varlıklar döner: hesap ACTIVE, gizli
 * değil ve e-postası doğrulanmış; gönderi silinmemiş ve arşivlenmemiş. Gizli
 * hesap istenirse {@code noindex} taşıyan sade bir belge döner — varlığı
 * doğrulanmaz, içeriği hiç yazılmaz.
 */
@Service
@RequiredArgsConstructor
public class SeoService {

    /** Sitemap'teki {@code lastmod} — W3C tarih biçimi (Google bunu bekler). */
    private static final DateTimeFormatter W3C = DateTimeFormatter.ISO_INSTANT;

    /** Profil belgesinde listelenecek gönderi sayısı — belge şişmesin. */
    private static final int PROFILE_POSTS = 24;

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostTagRepository tagRepository;
    private final PostSocialLinkRepository postSocialRepository;
    private final SocialLinkService socialLinkService;

    @Value("${waydee.seo.enabled:true}")
    private boolean enabled;

    @Value("${waydee.seo.site-url:http://localhost:5175}")
    private String siteUrl;

    @Value("${waydee.seo.sitemap-page-size:5000}")
    private int sitemapPageSize;

    public boolean isEnabled() {
        return enabled;
    }

    /** Sondaki eğik çizgi temizlenmiş kök adres — birleştirmeler buna dayanır. */
    public String site() {
        return siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    // ---------------------------------------------------------------- robots
    /**
     * {@code robots.txt}.
     *
     * <p>⚠️ Uygulama içi adresler ({@code /home}, {@code /settings}, {@code /map}…)
     * <b>bilinçli olarak kapalıdır</b>: hepsi oturum ister, bot yalnız boş bir
     * kabuk görür ve bu "ince içerik" sinyali <b>tüm alan adının</b> kalitesini
     * düşürür. Açık bırakılan yüzey vitrindir: {@code /{tag}} ve {@code /post/*}.
     */
    public String robots() {
        if (!enabled) {
            return "User-agent: *\nDisallow: /\n";
        }
        return """
                User-agent: *
                Allow: /$
                Allow: /post/
                Allow: /about
                Allow: /privacy
                Allow: /terms
                Disallow: /api/
                Disallow: /home
                Disallow: /me
                Disallow: /settings
                Disallow: /plan
                Disallow: /invoices
                Disallow: /notifications
                Disallow: /reports
                Disallow: /analytics
                Disallow: /saved
                Disallow: /map
                Disallow: /login
                Disallow: /register
                Disallow: /verify-email
                Disallow: /reset-password
                Disallow: /forgot-password
                Disallow: /payment/
                Disallow: /posts/*/edit
                Disallow: /*?tab=
                Disallow: /*?page=

                Sitemap: %s/sitemap.xml
                """.formatted(site());
    }

    // -------------------------------------------------------------- sitemap
    /** Site haritası indeksi — profiller ve gönderiler ayrı dosyalarda. */
    public String sitemapIndex() {
        long profiles = userRepository.findIndexableProfiles(PageRequest.of(0, 1)).getTotalElements();
        long posts = postRepository.findIndexablePosts(PageRequest.of(0, 1)).getTotalElements();
        StringBuilder sb = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        sb.append("  <sitemap><loc>").append(esc(site() + "/sitemap-pages.xml")).append("</loc></sitemap>\n");
        for (int i = 0; i < pages(profiles); i++) {
            sb.append("  <sitemap><loc>").append(esc(site() + "/sitemap-profiles-" + i + ".xml")).append("</loc></sitemap>\n");
        }
        for (int i = 0; i < pages(posts); i++) {
            sb.append("  <sitemap><loc>").append(esc(site() + "/sitemap-posts-" + i + ".xml")).append("</loc></sitemap>\n");
        }
        return sb.append("</sitemapindex>\n").toString();
    }

    /** Kurumsal sayfalar — sayısı sabit, veritabanına hiç gitmez. */
    public String sitemapPages() {
        StringBuilder sb = urlsetHeader();
        url(sb, site() + "/", null, "weekly", "1.0");
        url(sb, site() + "/about", null, "monthly", "0.5");
        url(sb, site() + "/privacy", null, "yearly", "0.2");
        url(sb, site() + "/terms", null, "yearly", "0.2");
        return sb.append("</urlset>\n").toString();
    }

    @Transactional(readOnly = true)
    public String sitemapProfiles(int page) {
        Page<User> users = userRepository.findIndexableProfiles(PageRequest.of(Math.max(page, 0), sitemapPageSize));
        StringBuilder sb = urlsetHeader();
        for (User u : users) {
            url(sb, site() + "/" + u.getUsername(), u.getUpdatedAt(), "daily", "0.8");
        }
        return sb.append("</urlset>\n").toString();
    }

    @Transactional(readOnly = true)
    public String sitemapPosts(int page) {
        Page<Post> posts = postRepository.findIndexablePosts(PageRequest.of(Math.max(page, 0), sitemapPageSize));
        StringBuilder sb = urlsetHeader();
        for (Post p : posts) {
            url(sb, site() + "/post/" + p.getId(), p.getUpdatedAt(), "weekly", "0.6");
        }
        return sb.append("</urlset>\n").toString();
    }

    private int pages(long total) {
        return total == 0 ? 1 : (int) Math.ceil((double) total / sitemapPageSize);
    }

    private static StringBuilder urlsetHeader() {
        return new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
    }

    private static void url(StringBuilder sb, String loc, Instant lastmod, String freq, String priority) {
        sb.append("  <url><loc>").append(esc(loc)).append("</loc>");
        if (lastmod != null) {
            sb.append("<lastmod>").append(W3C.format(lastmod.atOffset(ZoneOffset.UTC))).append("</lastmod>");
        }
        sb.append("<changefreq>").append(freq).append("</changefreq>")
                .append("<priority>").append(priority).append("</priority></url>\n");
    }

    // ------------------------------------------------------------ prerender
    /**
     * Bir uygulama adresinin <b>bot için</b> HTML karşılığı.
     *
     * <p>Tanınmayan adreslerde marka belgesi döner ({@code noindex}) —
     * boş sayfa yerine anlamlı bir yanıt, taramayı bozmaz.
     */
    @Transactional(readOnly = true)
    public Rendered render(String rawPath) {
        String path = normalize(rawPath);
        if (!enabled) {
            return new Rendered(brand(path, true), 200);
        }
        if (path.isEmpty() || path.equals("/")) {
            return new Rendered(landing(), 200);
        }
        if (path.startsWith("/post/")) {
            String id = path.substring("/post/".length());
            return parseUuid(id)
                    .flatMap(postRepository::findPublicPost)
                    .map(p -> new Rendered(postDocument(p), 200))
                    .orElseGet(() -> new Rendered(brand(path, true), 404));
        }
        // Tek segmentli her adres bir vitrin adayıdır (`/{tag}`).
        if (path.indexOf('/', 1) < 0) {
            String tag = path.substring(1).toLowerCase(Locale.ROOT);
            /* 🔴 16 Ağu 2026 — `hasPublicProfile` yönetim hesabını da eler.
               Site haritası yöneticiyi zaten atlıyordu ama BOT ÖN-RENDER'ı
               atlamıyordu: `/admin` isteyen bir tarayıcıya yöneticinin adı ve
               avatarıyla dolu bir HTML dönüyordu. */
            Optional<User> u = userRepository.findByUsername(tag)
                    .filter(User::hasPublicProfile);
            if (u.isPresent() && !u.get().isPrivateAccount()) {
                return new Rendered(profileDocument(u.get()), 200);
            }
            return new Rendered(brand(path, true), u.isPresent() ? 200 : 404);
        }
        return new Rendered(brand(path, true), 200);
    }

    /** Kullanıcı vitrini — sayfanın <b>gerçekten</b> gösterdiği metnin aynısı. */
    private String profileDocument(User u) {
        String tag = u.getUsername();
        String name = blank(u.getDisplayName()) ? tag : u.getDisplayName();
        String canonical = site() + "/" + tag;
        String description = blank(u.getBio())
                ? name + " · Waydee vitrini: fotoğrafları, ürün etiketleri ve bağlantıları."
                : u.getBio();
        String image = u.getAvatarMediaId() == null ? null
                : site() + "/api/v1/public/profiles/id/" + u.getId() + "/avatar";

        List<Post> posts = postRepository.findProfilePosts(u.getId(),
                PageRequest.of(0, PROFILE_POSTS)).getContent();
        List<SocialLinkView> social = socialLinkService.list(u.getId());

        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(esc(name)).append("</h1>\n")
                .append("<p class=\"handle\">@").append(esc(tag)).append("</p>\n");
        if (!blank(u.getBio())) {
            body.append("<p class=\"bio\">").append(esc(u.getBio())).append("</p>\n");
        }
        if (!social.isEmpty()) {
            body.append("<nav aria-label=\"Sosyal hesaplar\"><ul>\n");
            for (SocialLinkView s : social) {
                if (s.url() == null) continue;
                body.append("  <li><a rel=\"me nofollow\" href=\"").append(esc(s.url())).append("\">")
                        .append(esc(s.platform())).append("</a></li>\n");
            }
            body.append("</ul></nav>\n");
        }
        if (!posts.isEmpty()) {
            body.append("<h2>Gönderiler</h2>\n<ul class=\"posts\">\n");
            for (Post p : posts) {
                String title = blank(p.getTitle()) ? "Gönderi" : p.getTitle();
                body.append("  <li><a href=\"").append(esc(site() + "/post/" + p.getId())).append("\">");
                String cover = coverUrl(p);
                if (cover != null) {
                    body.append("<img src=\"").append(esc(cover)).append("\" alt=\"")
                            .append(esc(title)).append("\" width=\"320\" height=\"320\" loading=\"lazy\">");
                }
                body.append(esc(title)).append("</a></li>\n");
            }
            body.append("</ul>\n");
        }

        String jsonLd = profileJsonLd(u, name, canonical, image, social, posts);
        return document(name + " (@" + tag + ") · Waydee", description, canonical, image,
                "profile", body.toString(), jsonLd, false);
    }

    /** Tek gönderi — ürün etiketleri {@code Product/Offer} olarak işaretlenir. */
    private String postDocument(Post p) {
        User a = p.getAuthor();
        String authorName = blank(a.getDisplayName()) ? a.getUsername() : a.getDisplayName();
        String title = blank(p.getTitle()) ? authorName + " · Waydee gönderisi" : p.getTitle();
        String canonical = site() + "/post/" + p.getId();
        String image = coverUrl(p);
        List<PostTag> tags = tagRepository.findByPostIdOrderByPositionAsc(p.getId());
        List<PostSocialLink> social = postSocialRepository.findByPostIdOrderByPositionAsc(p.getId());

        String description = !blank(p.getCaption()) ? p.getCaption()
                : tags.isEmpty()
                ? authorName + " tarafından Waydee'de paylaşıldı."
                : authorName + " · " + tags.stream()
                        .map(t -> blank(t.getProductName()) ? host(t.getProductUrl()) : t.getProductName())
                        .filter(s -> s != null && !s.isBlank())
                        .distinct().limit(6).reduce((x, y) -> x + ", " + y).orElse("");

        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(esc(title)).append("</h1>\n")
                .append("<p class=\"author\">").append(esc(authorName))
                .append(" · <a href=\"").append(esc(site() + "/" + a.getUsername())).append("\">@")
                .append(esc(a.getUsername())).append("</a></p>\n");
        if (image != null) {
            body.append("<img src=\"").append(esc(image)).append("\" alt=\"").append(esc(title))
                    .append("\" width=\"1080\" height=\"1080\">\n");
        }
        if (!blank(p.getCaption())) {
            body.append("<p>").append(esc(p.getCaption())).append("</p>\n");
        }
        if (!tags.isEmpty()) {
            body.append("<h2>Bu gönderideki ürünler</h2>\n<ul class=\"products\">\n");
            for (PostTag t : tags) {
                String label = blank(t.getProductName()) ? host(t.getProductUrl()) : t.getProductName();
                body.append("  <li><a rel=\"nofollow sponsored\" href=\"").append(esc(t.getProductUrl()))
                        .append("\">").append(esc(label == null ? "Ürün" : label)).append("</a>");
                if (t.getPrice() != null) {
                    body.append(" <span class=\"price\">").append(esc(price(t.getPrice(), t.getCurrency()))).append("</span>");
                }
                body.append("</li>\n");
            }
            body.append("</ul>\n");
        }
        if (!social.isEmpty()) {
            body.append("<h2>Bağlantılar</h2>\n<ul class=\"social\">\n");
            for (PostSocialLink s : social) {
                String url = ProfilePostController.socialUrl(s.getPlatform(), s.getValue());
                if (url == null) continue;
                body.append("  <li><a rel=\"nofollow\" href=\"").append(esc(url)).append("\">")
                        .append(esc(s.getPlatform())).append("</a></li>\n");
            }
            body.append("</ul>\n");
        }

        return document(title + " · Waydee", description, canonical, image,
                "article", body.toString(), postJsonLd(p, title, canonical, image, tags, authorName), false);
    }

    private String landing() {
        String canonical = site() + "/";
        String description = "Waydee — fotoğraflarını alışverişe aç, tek adresten tüm bağlantılarını "
                + "paylaş ve dünya haritasında kendi mağazanı kur.";
        String body = """
                <h1>Waydee</h1>
                <p>Fotoğraflarını alışverişe aç: gönderilerine ürün etiketleri bırak, tüm
                bağlantılarını tek vitrin adresinde topla, Premium ile dünya haritasında
                kendi mağazanı aç.</p>
                <ul>
                  <li><a href="%s/about">Hakkımızda</a></li>
                  <li><a href="%s/terms">Kullanım koşulları</a></li>
                  <li><a href="%s/privacy">Gizlilik</a></li>
                </ul>
                """.formatted(site(), site(), site());
        String jsonLd = """
                {"@context":"https://schema.org","@type":"WebSite","name":"Waydee","url":"%s",
                "potentialAction":{"@type":"SearchAction","target":"%s/{search_term_string}",
                "query-input":"required name=search_term_string"}}"""
                .formatted(site(), site());
        return document("Waydee · Fotoğraflarını alışverişe aç", description, canonical, null,
                "website", body, jsonLd, false);
    }

    /** İndekslenmeyecek adresler için sade marka belgesi. */
    private String brand(String path, boolean noindex) {
        return document("Waydee", "Waydee", site() + path, null, "website",
                "<h1>Waydee</h1>\n", null, noindex);
    }

    // --------------------------------------------------------------- JSON-LD
    private String profileJsonLd(User u, String name, String canonical, String image,
                                 List<SocialLinkView> social, List<Post> posts) {
        StringBuilder sameAs = new StringBuilder();
        for (SocialLinkView s : social) {
            if (s.url() == null) continue;
            if (sameAs.length() > 0) sameAs.append(",");
            sameAs.append(json(s.url()));
        }
        StringBuilder items = new StringBuilder();
        int i = 0;
        for (Post p : posts) {
            if (items.length() > 0) items.append(",");
            items.append("{\"@type\":\"ListItem\",\"position\":").append(++i)
                    .append(",\"url\":").append(json(site() + "/post/" + p.getId())).append("}");
        }
        /* ⚠️ ProfilePage + Person iç içe yazılır: Google "kişi/oluşturucu"
           sonuçlarında Person'ı, site bağlantı kutusunda ProfilePage'i kullanır. */
        return """
                {"@context":"https://schema.org","@type":"ProfilePage","url":%s,
                "mainEntity":{"@type":"Person","name":%s,"alternateName":%s,"url":%s%s%s},
                "hasPart":{"@type":"ItemList","itemListElement":[%s]}}"""
                .formatted(json(canonical), json(name), json("@" + u.getUsername()), json(canonical),
                        image == null ? "" : ",\"image\":" + json(image),
                        sameAs.length() == 0 ? "" : ",\"sameAs\":[" + sameAs + "]",
                        items);
    }

    private String postJsonLd(Post p, String title, String canonical, String image,
                              List<PostTag> tags, String authorName) {
        StringBuilder products = new StringBuilder();
        for (PostTag t : tags) {
            String label = blank(t.getProductName()) ? host(t.getProductUrl()) : t.getProductName();
            if (label == null) continue;
            if (products.length() > 0) products.append(",");
            products.append("{\"@type\":\"Product\",\"name\":").append(json(label))
                    .append(",\"url\":").append(json(t.getProductUrl()));
            if (t.getPrice() != null) {
                products.append(",\"offers\":{\"@type\":\"Offer\",\"price\":")
                        .append(json(t.getPrice().toPlainString()))
                        .append(",\"priceCurrency\":")
                        .append(json(blank(t.getCurrency()) ? "TRY" : t.getCurrency()))
                        .append(",\"url\":").append(json(t.getProductUrl())).append("}");
            }
            products.append("}");
        }
        /* ⚠️ Tür `SocialMediaPosting`: bu bir haber makalesi değil, bir gönderi.
           Yanlış tür işaretlemek zengin sonuçtan tamamen düşürür. */
        return """
                {"@context":"https://schema.org","@type":"SocialMediaPosting","@id":%s,"url":%s,
                "headline":%s,"datePublished":%s,"author":{"@type":"Person","name":%s,"url":%s}%s%s%s}"""
                .formatted(json(canonical), json(canonical), json(title),
                        json(W3C.format(p.getCreatedAt().atOffset(ZoneOffset.UTC))),
                        json(authorName), json(site() + "/" + p.getAuthor().getUsername()),
                        image == null ? "" : ",\"image\":" + json(image),
                        blank(p.getCaption()) ? "" : ",\"articleBody\":" + json(p.getCaption()),
                        products.length() == 0 ? "" : ",\"mentions\":[" + products + "]");
    }

    // ------------------------------------------------------------- belge kabı
    /**
     * Ortak HTML iskeleti.
     *
     * <p>⚠️ Belgenin sonunda küçük bir yönlendirme betiği var: bu adrese
     * <b>gerçek bir tarayıcı</b> düşerse (bot tespiti yanıldıysa) uygulamaya
     * geçer. Bot JavaScript çalıştırmadığı için işaretlemeyi görmeye devam eder.
     */
    private String document(String title, String description, String canonical, String image,
                            String ogType, String body, String jsonLd, boolean noindex) {
        String desc = shorten(description, 300);
        return """
                <!doctype html>
                <html lang="tr">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <meta name="description" content="%s">
                <link rel="canonical" href="%s">
                <meta name="robots" content="%s">
                <meta property="og:site_name" content="Waydee">
                <meta property="og:type" content="%s">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:url" content="%s">
                %s<meta name="twitter:card" content="%s">
                <meta name="twitter:title" content="%s">
                <meta name="twitter:description" content="%s">
                %s%s</head>
                <body>
                <main>
                %s</main>
                <script>if(!/(bot|crawler|spider|preview|facebookexternalhit|slurp)/i.test(navigator.userAgent)){location.replace(%s);}</script>
                </body>
                </html>
                """.formatted(
                esc(title), esc(desc), esc(canonical),
                noindex ? "noindex, follow" : "index, follow, max-image-preview:large, max-snippet:-1",
                esc(ogType), esc(title), esc(desc), esc(canonical),
                image == null ? "" : "<meta property=\"og:image\" content=\"" + esc(image) + "\">\n",
                image == null ? "summary" : "summary_large_image",
                esc(title), esc(desc),
                image == null ? "" : "<meta name=\"twitter:image\" content=\"" + esc(image) + "\">\n",
                jsonLd == null ? "" : "<script type=\"application/ld+json\">" + jsonLd.replace("</", "<\\/") + "</script>\n",
                body, json(canonical + "?r=1"));
    }

    // ------------------------------------------------------------ yardımcılar
    private String coverUrl(Post p) {
        return p.getMedia().isEmpty() ? null : site() + "/api/v1/public/posts/" + p.getId() + "/image";
    }

    private static Optional<UUID> parseUuid(String v) {
        try {
            return Optional.of(UUID.fromString(v));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "/";
        String p = raw.trim();
        int q = p.indexOf('?');
        if (q >= 0) p = p.substring(0, q);
        if (!p.startsWith("/")) p = "/" + p;
        // Sondaki eğik çizgi kanonik değildir: `/ali/` ile `/ali` aynı sayfadır.
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? null : h.replaceFirst("^www\\.", "");
        } catch (Exception ex) {
            return null;
        }
    }

    private static String price(BigDecimal value, String currency) {
        return value.toPlainString() + " " + (blank(currency) ? "TRY" : currency);
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private static String shorten(String v, int max) {
        String s = v == null ? "" : v.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** HTML/XML kaçışı — hem öznitelik hem gövde için güvenli. */
    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /** JSON dize sabiti (tırnaklar dahil) — JSON-LD içine gömmek için. */
    private static String json(String v) {
        if (v == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Üretilen belge + HTTP durumu (yok olan kaynak için 404 şart). */
    public record Rendered(String html, int status) {
    }
}
