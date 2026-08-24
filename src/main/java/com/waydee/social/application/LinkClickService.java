package com.waydee.social.application;

import com.waydee.common.net.ClientIpResolver;
import com.waydee.common.storage.MediaUrls;
import com.waydee.social.api.dto.LinkStatsDtos.CountryRow;
import com.waydee.social.api.dto.LinkStatsDtos.DayRow;
import com.waydee.social.api.dto.LinkStatsDtos.LinkRow;
import com.waydee.social.api.dto.LinkStatsDtos.LinkStatsResponse;
import com.waydee.social.api.dto.LinkStatsDtos.VisitorRow;
import com.waydee.social.domain.ProfileLink;
import com.waydee.social.domain.ProfileLinkClick;
import com.waydee.social.infrastructure.ProfileLinkClickRepository;
import com.waydee.social.infrastructure.ProfileLinkRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Bağlantı tıklama ölçümü ve raporu</b> (V46).
 *
 * <h3>🔴 Neden yazıldı</h3>
 * <p>Kullanıcı: <i>"link tıklama analizi inanılmaz sorunlu, baştan sona
 * düzelt."</i> Diskten doğrulandı: {@code profile_links.click_count} kolonu
 * vardı, API'de dönüyordu, arayüzde gösteriliyordu — ama <b>hiçbir yerde
 * artırılmıyordu</b>. Ortada bozuk bir ölçüm değil, <b>hiç olmayan</b> bir
 * ölçüm vardı; kullanıcı yıllardır sabit sıfıra bakıyordu.
 *
 * <h3>Ölçümün üç kuralı</h3>
 * <ol>
 *   <li><b>Şişirilemez:</b> aynı ziyaretçi aynı bağlantıya
 *       {@value #DEDUPE_SECONDS} saniye içinde tekrar tıklarsa sayılmaz.
 *       Bu olmadan sayfayı yenileyip tıklayan biri rakamı istediği yere
 *       çekerdi ve rapor bir ölçüm olmaktan çıkardı.</li>
 *   <li><b>Ham IP saklanmaz:</b> ziyaretçi anahtarı IP + tarayıcı imzasının
 *       SHA-256'sıdır. Saymak için ayırt etmek yeter, kimliği tutmak
 *       gerekmez.</li>
 *   <li><b>Ülke KOD olarak saklanır</b>, ad olarak değil — adı istemci kendi
 *       dilinde üretir.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkClickService {

    /** Aynı ziyaretçinin aynı bağlantıya tekrar tıklaması bu süre boyunca sayılmaz. */
    public static final int DEDUPE_SECONDS = 60;

    /** Raporun "son tıklayanlar" listesinde en fazla kaç satır. */
    private static final int RECENT_LIMIT = 50;

    private final ProfileLinkRepository linkRepository;
    private final ProfileLinkClickRepository clickRepository;
    private final ClientIpResolver clientIpResolver;

    /* ------------------------------------------------------------------ yazma */

    /**
     * Tıklamayı kaydeder.
     *
     * <p>⚠️ <b>Senkron</b> ve bilinçli: iş tek bir INSERT + tek bir sayaç
     * UPDATE'i. Bir thread havuzuna atmak, kaybı kabul edilebilir bir ölçüm
     * (görüntülenme) için doğru olurdu — <b>tıklama ise ürünün KPI'ıdır</b> ve
     * {@code analyticsExecutor}'ın "dolunca sessizce at" politikası burada
     * yanlış olurdu. İstemci zaten yanıtı beklemeden hedefe gidiyor.
     *
     * <p>⚠️ Bilinmeyen/pasif bağlantıda sessizce döner: tıklama ölçümü
     * ziyaretçinin akışını <b>hiçbir koşulda</b> kesmemeli.
     */
    @Transactional
    public void record(UUID linkId, UUID viewerUserId, HttpServletRequest request) {
        ProfileLink link = linkRepository.findById(linkId).orElse(null);
        if (link == null || !link.isActive()) {
            return;
        }
        String visitorKey = visitorKey(request);
        Instant since = Instant.now().minusSeconds(DEDUPE_SECONDS);
        if (clickRepository.existsByLinkIdAndVisitorKeyAndCreatedAtAfter(linkId, visitorKey, since)) {
            return; // aynı ziyaretçi, aynı bağlantı, çok kısa süre → tek tıklama
        }
        clickRepository.save(new ProfileLinkClick(
                linkId, link.getOwnerId(), viewerUserId, countryCode(request), visitorKey));
        /* Toplam sayaç ayrıca artar: kart üstünde tek sayı göstermek için tek
           satırlık okuma yeterli olmalı (V44'teki aynı ilke). */
        linkRepository.bumpClickCount(linkId);
    }

    /* ------------------------------------------------------------------ okuma */

    /**
     * Sahibin raporu.
     *
     * <p>⚠️ Bağlantı listesi <b>tıklaması olmayanları da</b> içerir: rapor
     * "hangi bağlantım işe yaramıyor" sorusunu da yanıtlamalı. Sıfır tıklamalı
     * bir bağlantıyı listeden düşürmek, en önemli bulguyu gizlerdi.
     */
    @Transactional(readOnly = true)
    public LinkStatsResponse report(UUID ownerId, int days) {
        int window = Math.min(Math.max(days, 1), 365);
        Instant since = Instant.now().minus(Duration.ofDays(window));

        long clicks = clickRepository.countInPeriod(ownerId, since);
        long visitors = clickRepository.countVisitorsInPeriod(ownerId, since);

        Map<UUID, long[]> perLink = new HashMap<>();
        Map<UUID, Instant> lastAt = new HashMap<>();
        for (Object[] row : clickRepository.totalsByLink(ownerId, since)) {
            UUID id = (UUID) row[0];
            perLink.put(id, new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
            lastAt.put(id, (Instant) row[3]);
        }

        List<LinkRow> links = new ArrayList<>();
        for (ProfileLink link : linkRepository.findByOwnerIdOrderByPositionAsc(ownerId)) {
            long[] totals = perLink.getOrDefault(link.getId(), new long[]{0, 0});
            links.add(new LinkRow(link.getId(), link.getTitle(), link.getUrl(),
                    link.getIconMediaId() == null ? null : MediaUrls.of(link.getIconMediaId()),
                    totals[0], totals[1], link.getClickCount(), lastAt.get(link.getId())));
        }
        // En çok tıklanan üstte — raporun ilk sorusu "hangisi çalışıyor".
        links.sort((a, b) -> Long.compare(b.clicks(), a.clicks()));

        List<DayRow> daily = new ArrayList<>();
        for (Object[] row : clickRepository.dailyTotals(ownerId, since)) {
            daily.add(new DayRow(toLocalDate(row[0]), ((Number) row[1]).longValue()));
        }

        List<CountryRow> countries = new ArrayList<>();
        for (Object[] row : clickRepository.countryTotals(ownerId, since)) {
            countries.add(new CountryRow((String) row[0], ((Number) row[1]).longValue()));
        }

        List<VisitorRow> recent = new ArrayList<>();
        long identified = 0;
        for (Object[] row : clickRepository.recentClicks(ownerId, since, PageRequest.of(0, RECENT_LIMIT))) {
            UUID userId = (UUID) row[2];
            if (userId != null) {
                identified++;
            }
            recent.add(new VisitorRow(
                    (Instant) row[0], (String) row[1], userId, (String) row[3], (String) row[4],
                    row[5] == null ? null : MediaUrls.of((UUID) row[5]),
                    (String) row[6]));
        }

        return new LinkStatsResponse(window, clicks, visitors, identified, daily, countries, links, recent);
    }

    /* ------------------------------------------------------------- yardımcılar */

    /**
     * <b>ISO 3166-1 alpha-2 kodu</b> — CDN/yük dengeleyicinin eklediği başlıktan.
     *
     * <p>🔴 Mevcut {@code ClientInfo.country()} <b>kullanılmıyor</b>: o metot
     * Türkçe bir ülke <b>adı</b> döndürüyor ("Almanya") ve bu değer beş dilli
     * bir arayüzde basılamaz. Burada ham kod saklanır.
     *
     * <p>⚠️ Harici bir IP→ülke servisine çıkılmaz (gizlilik + bağımlılık);
     * başlık yoksa ülke <b>bilinmiyor</b> kalır ve rapor bunu dürüstçe öyle
     * gösterir.
     */
    private String countryCode(HttpServletRequest request) {
        for (String header : new String[]{"CF-IPCountry", "X-Country-Code", "X-Geo-Country", "X-AppEngine-Country"}) {
            /* 🔒 17 Ağu 2026 — başlık YALNIZ güvenilir vekilin arkasında okunur.
               Uç kimliksizdir; koşulsuz okumak, herhangi bir ziyaretçinin
               "hangi ülkelerden tıklandı" raporunu istediği gibi doldurması
               demekti (rapor ölçüm olmaktan çıkardı). */
            String value = clientIpResolver.trustedHeader(request, header);
            if (value != null && value.length() == 2 && !"XX".equalsIgnoreCase(value)) {
                return value.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /**
     * Ziyaretçi anahtarı: {@code SHA-256(ip + "|" + user-agent)}.
     *
     * <p>⚠️ Ham IP <b>saklanmaz</b>. Tekil ziyaretçiyi saymak ve tekrarı
     * bastırmak için ayırt edici bir dize yeterli; kişisel veriyi tutmanın
     * ölçüme hiçbir katkısı yok.
     */
    private String visitorKey(HttpServletRequest request) {
        /* 🔴 17 Ağu 2026 — DENETİM BULGUSU: anahtar en soldaki
           `X-Forwarded-For` girdisinden üretiliyordu. O girdiyi istemci
           yazdığı için başlığı her istekte değiştiren biri ziyaretçi
           anahtarını da değiştiriyor ve 60 saniyelik tekrar bastırmayı
           tamamen atlayarak sayacı istediği yere çekebiliyordu — yani bu
           turda kurulan ölçümü doğduğu gün etkisiz kılan bir açıktı. */
        String ip = clientIpResolver.resolve(request);
        String agent = request.getHeader("User-Agent");
        return sha256((ip == null ? "?" : ip) + "|" + (agent == null ? "?" : agent));
    }

    private static String sha256(String raw) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (Exception e) {
            // SHA-256 her JVM'de vardır; buraya düşmek imkânsıza yakın.
            throw new IllegalStateException("SHA-256 yok", e);
        }
    }

    /** {@code date_trunc} sürücüye göre {@code Timestamp} ya da {@code Instant} döner. */
    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }
}
