package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.identity.domain.FollowStatus;
import com.waydee.identity.infrastructure.FollowRepository;
import com.waydee.social.api.dto.AnalyticsDtos.AnalyticsResponse;
import com.waydee.social.api.dto.AnalyticsDtos.DayCount;
import com.waydee.social.api.dto.AnalyticsDtos.LabelCount;
import com.waydee.social.api.dto.AnalyticsDtos.ReportResponse;
import com.waydee.social.api.dto.AnalyticsDtos.TerritoryStat;
import com.waydee.social.api.dto.AnalyticsDtos.TopViewer;
import com.waydee.social.api.dto.AnalyticsDtos.ViewerRow;
import com.waydee.social.domain.TerritoryView;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.PostTagRepository;
import com.waydee.social.infrastructure.CollectionRepository;
import com.waydee.social.infrastructure.ProfileLinkRepository;
import com.waydee.social.infrastructure.StoryRepository;
import com.waydee.social.infrastructure.TerritoryViewRepository;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.domain.TerritoryStatus;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bölge (profil) görüntüleme raporlaması. Görüntülemeler throttle'lı kaydedilir
 * (kullanıcı başına saatte bir), sahibine "profil görüntüleme" bildirimi düşer.
 */
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AnalyticsService {

    /*
     * 🔴 KALDIRILDI (V54): kısma artık SÜRE değil GÜN üzerinden ve kuralı
     * veritabanındaki tekil indeks uyguluyor. Sabiti bırakmak, "hangisi
     * geçerli?" sorusunu kodda iki yere bölerdi.
     */
    private static final int REPORT_DAYS = 14;

    private final TerritoryViewRepository viewRepository;
    private final TerritoryRepository territoryRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final StoryRepository storyRepository;
    private final PostTagRepository postTagRepository;
    private final CollectionRepository collectionRepository;
    private final ProfileLinkRepository profileLinkRepository;

    /**
     * Bir profil görüntülemesini kaydeder (sahibi hariç, throttle'lı) + bildirim.
     * Async: sıcak GET yolunda yazma transaction'ı açıp bağlantı havuzunu tüketmesin.
     */
    /**
     * <b>Vitrin profiline yapılan ziyareti ÖLÇER</b> (21 Ağu 2026).
     *
     * <h3>🔴 Yaşanmış hata: gösterilen ama artmayan sayaç</h3>
     * <p>Kullanıcı bir bildirim aldı — *"X bölgeni görüntüledi"* — ve raporda
     * <b>0 görüntülenme</b> gördü. Sebep: {@code waydee.com/<kullanıcıadı>}
     * ziyareti yalnız <b>bildirim</b> yazıyordu ({@code notifyProfileView}),
     * hiçbir {@code TerritoryView} kaydetmiyordu. Rapor ise yalnız
     * {@code TerritoryView} sayıyor. Yani ürün bir ölçümü <b>duyuruyor ama
     * tutmuyordu</b>.
     *
     * <p>✅ Doğru okuma: bu üründe mağaza <b>profilin kendisidir</b> (V38 —
     * daire km² ile satılan bir alan değil, kullanıcının sosyal profili).
     * Dolayısıyla profiline bakmak, mağazasına bakmaktır ve <b>sayılmalıdır</b>.
     *
     * <p>⚠️ Mağazası olmayan kullanıcı için sayılacak bir bölge yoktur; o
     * durumda yalnız bildirim yazılır (eski davranış). Rapor bölgeler
     * üzerinedir ve bu, verinin doğal sınırıdır.
     *
     * <p>⚠️ {@code Async}: çağrı bir <b>okuma</b> yolundan (profil GET'i)
     * gelir; yazma transaction'ı sıcak yolda bağlantı havuzunu tüketmemeli.
     */
    @org.springframework.scheduling.annotation.Async("analyticsExecutor")
    @Transactional
    public void recordProfileView(UUID ownerId, UUID viewerId) {
        if (viewerId == null || viewerId.equals(ownerId)) {
            return;
        }
        Territory target = territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId).stream()
                .filter(t -> t.getStatus() == com.waydee.territory.domain.TerritoryStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        if (target == null) {
            /*
             * Mağazası yok: ölçülecek bir bölge de yok.
             *
             * 🔴 Eskiden burada bir BİLDİRİM yazılıyordu; 24 Ağu 2026'da
             * profil görüntüleme bildirimi tamamen kaldırıldı. Geriye yapacak
             * bir şey kalmadı — rapor bölgeler üzerinedir ve bu, verinin
             * doğal sınırıdır.
             */
            return;
        }
        /* 🔴 `recordView` hem ÖLÇER hem BİLDİRİR ve kısmayı ikisinde de
           uygular — bildirimi burada ayrıca yazmak çift satır üretirdi. */
        recordView(target.getId(), viewerId);
    }

    @org.springframework.scheduling.annotation.Async("analyticsExecutor")
    @Transactional
    public void recordView(UUID territoryId, UUID viewerId) {
        if (viewerId == null) {
            return;
        }
        Territory territory = territoryRepository.findWithOwnerById(territoryId).orElse(null);
        if (territory == null) {
            return;
        }
        UUID ownerId = territory.getOwner().getId();
        if (viewerId.equals(ownerId)) {
            return;
        }
        /*
         * 🔴 24 Ağu 2026 — GÜNDE BİR KEZ (V54).
         *
         * Kullanıcı: *"günde ben 10 kere baktıysam bu 10 kere sayılmasın,
         * 1 kere sayılsın"*. Eski kısma 60 dakikalıktı — aynı kişiyi günde
         * 24 kez sayabiliyordu.
         *
         * ⚠️ Aşağıdaki kontrol yalnız bir <b>ön elemedir</b>. Gerçek kuralı
         * {@code uq_territory_views_daily} tekil indeksi uyguluyor: kontrol
         * ile INSERT arasına başka bir istek girerse (iki sekme, hızlı
         * yenileme) uygulama kontrolü sessizce başarısız olurdu.
         */
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        if (viewRepository.existsByTerritoryIdAndViewerIdAndViewDay(territoryId, viewerId, today)) {
            return;
        }
        try {
            viewRepository.saveAndFlush(new TerritoryView(territoryId, viewerId));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            /* Yarışı kaybettik: aynı gün için satır zaten var. Bu bir hata
               değil, kuralın ta kendisi — sessizce geç.
               ⚠️ `saveAndFlush` şart: `save` yazmayı transaction sonuna
               erteler ve ihlal BURADA değil, çağıranın hiç yakalayamayacağı
               bir yerde patlardı. */
            log.debug("Görüntüleme zaten sayılmış (günlük tekil): {} / {}", territoryId, viewerId);
        }
        /*
         * 🔴 BİLDİRİM KALDIRILDI (24 Ağu 2026). Kullanıcı: *"bir hesaba bakma
         * bildirim özelliği kaldırılsın, bildirim gelmesin, sadece
         * istatistiklerde görünsün"*.
         *
         * ⚠️ Ölçüm DURUYOR — kaldırılan yalnız duyurudur. 21 Ağu'da bunun
         * tersi bir hata yaşanmıştı (duyuruluyor ama tutulmuyordu); şimdi
         * tutuluyor ama duyurulmuyor. İkisi ayrı kararlardır.
         */
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse territoryAnalytics(UUID territoryId, UUID ownerId) {
        Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        if (!territory.getOwner().getId().equals(ownerId)) {
            throw ApiException.forbidden("Bu raporu görme yetkiniz yok");
        }
        long total = viewRepository.countByTerritoryId(territoryId);
        long unique = viewRepository.countDistinctViewers(territoryId);
        List<DayCount> byDay = groupByDay(
                viewRepository.findByTerritoryIdAndViewedAtAfterOrderByViewedAtAsc(territoryId, reportSince()));
        List<ViewerRow> recent = recentViewers(viewRepository.findTop50ByTerritoryIdOrderByViewedAtDesc(territoryId));
        return new AnalyticsResponse(total, unique, byDay, recent);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse myAnalytics(UUID ownerId) {
        List<UUID> ids = territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId).stream()
                .map(Territory::getId).toList();
        if (ids.isEmpty()) {
            return new AnalyticsResponse(0, 0, groupByDay(List.of()), List.of());
        }
        long total = viewRepository.countByTerritoryIdIn(ids);
        long unique = viewRepository.countDistinctViewersIn(ids);
        List<DayCount> byDay = groupByDay(
                viewRepository.findByTerritoryIdInAndViewedAtAfterOrderByViewedAtAsc(ids, reportSince()));
        List<ViewerRow> recent = recentViewers(viewRepository.findTop50ByTerritoryIdInOrderByViewedAtDesc(ids));
        return new AnalyticsResponse(total, unique, byDay, recent);
    }

    /**
     * Kullanıcının tam raporu — görüntülenme trendi, bölge kırılımı, kitle ve
     * içerik etkileşimi tek çağrıda. Dönem `days` ile seçilir (7/14/30/90).
     *
     * Maliyet: sabit sayıda toplu (GROUP BY) sorgu — bölge/görüntüleyen başına
     * ek sorgu yapılmaz.
     */
    @Transactional(readOnly = true)
    public ReportResponse report(UUID ownerId, int requestedDays) {
        int days = clampDays(requestedDays);
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(days));
        Instant prevSince = now.minus(Duration.ofDays(2L * days));

        List<Territory> owned = territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId);
        List<UUID> ids = owned.stream().map(Territory::getId).toList();

        long followers = followRepository.countByFolloweeIdAndStatus(ownerId, FollowStatus.ACCEPTED);
        long following = followRepository.countByFollowerIdAndStatus(ownerId, FollowStatus.ACCEPTED);
        long pending = followRepository.countByFolloweeIdAndStatus(ownerId, FollowStatus.PENDING);

        Object[] engagement = postRepository.engagementByAuthor(ownerId);
        // JPQL çok sütunlu tek satır: bazı sürücülerde Object[] { Object[] } olarak sarılır.
        Object[] e = engagement != null && engagement.length == 1 && engagement[0] instanceof Object[] inner
                ? inner : engagement;
        long postCount = e != null && e.length > 0 ? ((Number) e[0]).longValue() : 0;
        long likes = e != null && e.length > 1 ? ((Number) e[1]).longValue() : 0;
        long comments = e != null && e.length > 2 ? ((Number) e[2]).longValue() : 0;
        long postsInPeriod = postRepository.countByAuthorIdAndDeletedAtIsNullAndCreatedAtAfter(ownerId, since);
        long activeStories = storyRepository.findActiveByAuthor(ownerId, now).size();

        BigDecimal totalArea = owned.stream()
                .filter(t -> t.getStatus() == TerritoryStatus.ACTIVE)
                .map(Territory::getAreaKm2)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = owned.stream()
                .filter(t -> t.getStatus() == TerritoryStatus.ACTIVE)
                .map(Territory::getPricePaid)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = owned.stream()
                .map(Territory::getCurrency)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("TRY");

        if (ids.isEmpty()) {
            // Bölge yoksa görüntülenme de yoktur; kitle/içerik verisi yine döner.
            return new ReportResponse(days, 0, 0, 0, 0, null, emptyDays(days), List.of(), null,
                    0, BigDecimal.ZERO, BigDecimal.ZERO, currency, List.of(),
                    followers, following, pending,
                    postCount, postsInPeriod, likes, comments, activeStories,
                    // Bölge yoksa da etiket/kaydetme sayıları anlamlıdır.
                    postTagRepository.countByOwner(ownerId),
                    postRepository.sumSaveCountByAuthor(ownerId),
                    collectionRepository.countByOwnerId(ownerId),
                    profileLinkRepository.countByOwnerId(ownerId),
                    List.of(), List.of());
        }

        long total = viewRepository.countByTerritoryIdIn(ids);
        long unique = viewRepository.countDistinctViewersIn(ids);
        long inPeriod = viewRepository.countByTerritoryIdInAndViewedAtBetween(ids, since, now);
        long prevPeriod = viewRepository.countByTerritoryIdInAndViewedAtBetween(ids, prevSince, since);
        // Önceki dönem sıfırsa yüzde değişim tanımsızdır (0'a bölme) → null.
        Double change = prevPeriod == 0 ? null : ((inPeriod - prevPeriod) * 100.0) / prevPeriod;

        List<Instant> times = viewRepository.viewTimes(ids, since);
        List<DayCount> byDay = groupByDay(times, days);
        List<LabelCount> byWeekday = groupByWeekday(times);
        Integer busiestHour = busiestHour(times);

        Map<UUID, long[]> perTerritory = new java.util.HashMap<>();
        for (Object[] row : viewRepository.statsByTerritory(ids, since)) {
            perTerritory.put((UUID) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        List<TerritoryStat> territoryStats = owned.stream()
                .map(t -> {
                    long[] s = perTerritory.getOrDefault(t.getId(), new long[]{0, 0});
                    return new TerritoryStat(t.getId(), t.getName(), null, t.getAreaKm2(), s[0], s[1]);
                })
                .sorted(Comparator.comparingLong(TerritoryStat::views).reversed())
                .toList();

        List<TopViewer> top = topViewers(ids, since);
        List<ViewerRow> recent = recentViewers(viewRepository.findTop50ByTerritoryIdInOrderByViewedAtDesc(ids));

        return new ReportResponse(days, total, unique, inPeriod, prevPeriod, change,
                byDay, byWeekday, busiestHour,
                owned.size(), totalArea, value, currency, territoryStats,
                followers, following, pending,
                postCount, postsInPeriod, likes, comments, activeStories,
                // Analytics ekranı bu dört sayıyı üst kartlarda gösterir.
                postTagRepository.countByOwner(ownerId),
                postRepository.sumSaveCountByAuthor(ownerId),
                collectionRepository.countByOwnerId(ownerId),
                profileLinkRepository.countByOwnerId(ownerId),
                top, recent);
    }

    private static int clampDays(int days) {
        if (days <= 7) return 7;
        if (days <= 14) return 14;
        if (days <= 30) return 30;
        return 90;
    }

    private List<TopViewer> topViewers(List<UUID> ids, Instant since) {
        List<Object[]> rows = viewRepository.topViewers(ids, since,
                org.springframework.data.domain.PageRequest.of(0, 8));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> viewerIds = rows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, User> users = userRepository.findAllById(viewerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream()
                .filter(r -> users.containsKey((UUID) r[0]))
                .map(r -> new TopViewer(UserSummary.from(users.get((UUID) r[0])),
                        ((Number) r[1]).longValue(), (Instant) r[2]))
                .toList();
    }

    private static List<DayCount> emptyDays(int days) {
        List<DayCount> out = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) {
            out.add(new DayCount(today.minusDays(i).toString(), 0));
        }
        return out;
    }

    private static List<DayCount> groupByDay(List<Instant> times, int days) {
        Map<LocalDate, Long> counts = times.stream()
                .collect(Collectors.groupingBy(t -> t.atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<DayCount> out = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            out.add(new DayCount(day.toString(), counts.getOrDefault(day, 0L)));
        }
        return out;
    }

    private static final String[] WEEKDAYS = {"Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"};

    private static List<LabelCount> groupByWeekday(List<Instant> times) {
        long[] counts = new long[7];
        for (Instant t : times) {
            counts[t.atZone(ZoneOffset.UTC).getDayOfWeek().getValue() - 1]++;
        }
        List<LabelCount> out = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            out.add(new LabelCount(WEEKDAYS[i], counts[i]));
        }
        return out;
    }

    private static Integer busiestHour(List<Instant> times) {
        if (times.isEmpty()) {
            return null;
        }
        long[] hours = new long[24];
        for (Instant t : times) {
            hours[t.atZone(ZoneOffset.UTC).getHour()]++;
        }
        int best = 0;
        for (int i = 1; i < 24; i++) {
            if (hours[i] > hours[best]) best = i;
        }
        return hours[best] == 0 ? null : best;
    }

    private Instant reportSince() {
        return Instant.now().minus(Duration.ofDays(REPORT_DAYS));
    }

    private List<DayCount> groupByDay(List<TerritoryView> views) {
        Map<LocalDate, Long> counts = views.stream()
                .collect(Collectors.groupingBy(v -> v.getViewedAt().atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));
        List<DayCount> out = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = REPORT_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            out.add(new DayCount(day.toString(), counts.getOrDefault(day, 0L)));
        }
        return out;
    }

    private List<ViewerRow> recentViewers(List<TerritoryView> views) {
        // desc sıralı geldiği için ilk görülen = en yeni; tekilleştir.
        LinkedHashMap<UUID, Instant> latest = new LinkedHashMap<>();
        for (TerritoryView view : views) {
            latest.putIfAbsent(view.getViewerId(), view.getViewedAt());
        }
        Map<UUID, User> users = userRepository.findAllById(latest.keySet()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return latest.entrySet().stream()
                .filter(e -> users.containsKey(e.getKey()))
                .map(e -> new ViewerRow(UserSummary.from(users.get(e.getKey())), e.getValue()))
                .toList();
    }
}
