package com.waydee.social.application;

import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.FollowRepository;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.dto.TrendingDtos.TrendingItem;
import com.waydee.social.domain.TrendingEntry;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.TerritoryLikeRepository;
import com.waydee.social.infrastructure.TerritorySaveRepository;
import com.waydee.social.infrastructure.TerritoryViewRepository;
import com.waydee.social.infrastructure.TrendingRepository;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Yükselişte / revaçta" sıralaması.
 *
 * <h3>Skor formülü</h3>
 * <pre>
 *   ham = 1.0·görüntülenme + 4.0·beğeni + 6.0·kaydetme + 3.0·gönderi + 5.0·yeni takipçi
 *   skor = ham × tazelik × yeniGelenDesteği
 * </pre>
 *
 * <h3>Ağırlıklar neden böyle</h3>
 * Görüntülenme en ucuz sinyaldir (kazara da olur) → ağırlığı 1. Beğeni bilinçli
 * bir eylemdir → 4. <b>Kaydetme en pahalı sinyaldir</b> (kullanıcı geri dönmeyi
 * planlıyor) → 6. Gönderi sahibin canlılığını gösterir → 3. Yeni takipçi kalıcı
 * ilgi demektir → 5.
 *
 * <h3>Tazelik</h3>
 * Sinyaller 7 günlük pencereden okunur ve son 48 saatte olanlar
 * <b>1.5×</b> sayılır. Böylece "toplamda popüler" değil <b>şu an yükselen</b>
 * içerik öne çıkar — aksi halde liste eski ve büyük dairelerde donardı.
 *
 * <h3>Yeni gelen desteği</h3>
 * 14 günden yeni özneler <b>1.25×</b> alır. Bu olmadan yeni açılan hiçbir daire
 * listeye giremez ve sıralama kendini kilitler (zengin daha zengin olur).
 *
 * <h3>Eşik</h3>
 * Skoru {@link #MIN_SCORE} altındakiler listeye alınmaz — tek görüntülenmeyle
 * "trend" olmak duyuru şeridini anlamsızlaştırırdı.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendingService {

    public static final String TERRITORY = "TERRITORY";
    public static final String USER = "USER";

    private static final int WINDOW_DAYS = 7;
    private static final int FRESH_HOURS = 48;
    private static final double FRESH_MULTIPLIER = 1.5;
    private static final int NEWCOMER_DAYS = 14;
    private static final double NEWCOMER_MULTIPLIER = 1.25;
    private static final double MIN_SCORE = 5.0;
    private static final int MAX_ENTRIES = 20;

    private static final double W_VIEW = 1.0;
    private static final double W_LIKE = 4.0;
    private static final double W_SAVE = 6.0;
    private static final double W_POST = 3.0;
    private static final double W_FOLLOW = 5.0;

    private final TerritoryRepository territoryRepository;
    private final TerritoryViewRepository viewRepository;
    private final TerritoryLikeRepository likeRepository;
    private final TerritorySaveRepository saveRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final TrendingRepository trendingRepository;

    // ------------------------------------------------------------ hesaplama

    /**
     * Skorları yeniden hesaplar ve sıralamayı yazar.
     *
     * @return yazılan girdi sayısı
     */
    @Transactional
    public int recompute() {
        Instant now = Instant.now();
        Instant since = now.minus(WINDOW_DAYS, ChronoUnit.DAYS);
        Instant freshSince = now.minus(FRESH_HOURS, ChronoUnit.HOURS);
        Instant newcomerSince = now.minus(NEWCOMER_DAYS, ChronoUnit.DAYS);

        // Önceki sıraları sakla — "kaç basamak yükseldi" bilgisi bundan çıkar.
        Map<String, Integer> previous = new HashMap<>();
        trendingRepository.findAll().forEach(e -> previous.put(key(e.getSubjectType(), e.getSubjectId()), e.getRank()));

        List<Territory> territories = territoryRepository.findAllVisibleWithOwner();
        if (territories.isEmpty()) {
            trendingRepository.deleteAll();
            return 0;
        }

        Map<UUID, Signals> byTerritory = new HashMap<>();
        territories.forEach(t -> byTerritory.put(t.getId(), new Signals()));

        // --- görüntülenme (pencere + taze pencere ayrı)
        applyCounts(viewRepository.countsByTerritorySince(since), byTerritory, (s, n) -> s.views += n);
        applyCounts(viewRepository.countsByTerritorySince(freshSince), byTerritory, (s, n) -> s.freshViews += n);
        // --- beğeni / kaydetme
        applyCounts(likeRepository.countsSince(since), byTerritory, (s, n) -> s.likes += n);
        applyCounts(likeRepository.countsSince(freshSince), byTerritory, (s, n) -> s.freshLikes += n);
        applyCounts(saveRepository.countsSince(since), byTerritory, (s, n) -> s.saves += n);
        applyCounts(saveRepository.countsSince(freshSince), byTerritory, (s, n) -> s.freshSaves += n);
        // --- gönderi
        applyCounts(postRepository.countsByTerritorySince(since), byTerritory, (s, n) -> s.posts += n);

        // --- yeni takipçi: sahibi üzerinden (daire sahibinin çektiği ilgi daireye de yansır)
        Map<UUID, Long> newFollowers = new HashMap<>();
        for (Object[] row : followRepository.acceptedCountsSince(since)) {
            newFollowers.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        List<TrendingEntry> entries = new ArrayList<>();
        List<Scored> scoredTerritories = new ArrayList<>();

        for (Territory t : territories) {
            Signals s = byTerritory.get(t.getId());
            s.followers = newFollowers.getOrDefault(t.getOwner().getId(), 0L);
            boolean newcomer = t.getPurchasedAt().isAfter(newcomerSince);
            double score = s.score(newcomer);
            if (score < MIN_SCORE) {
                continue;
            }
            scoredTerritories.add(new Scored(t.getId(), score, s));
        }

        scoredTerritories.sort(Comparator.comparingDouble((Scored x) -> x.score).reversed());
        int rank = 0;
        for (Scored x : scoredTerritories) {
            if (rank >= MAX_ENTRIES) {
                break;
            }
            rank++;
            TrendingEntry e = new TrendingEntry(TERRITORY, x.id);
            e.setScore(BigDecimal.valueOf(x.score).setScale(4, RoundingMode.HALF_UP));
            e.setRank(rank);
            e.setPreviousRank(previous.get(key(TERRITORY, x.id)));
            e.setViews7d((int) x.s.views);
            e.setLikes7d((int) x.s.likes);
            e.setSaves7d((int) x.s.saves);
            e.setPosts7d((int) x.s.posts);
            e.setFollowers7d((int) x.s.followers);
            e.setComputedAt(now);
            entries.add(e);
        }

        // --- kullanıcılar: sahibi oldukları dairelerin skorlarının toplamı + kendi takipçi ivmesi
        Map<UUID, Double> userScores = new HashMap<>();
        Map<UUID, Signals> userSignals = new HashMap<>();
        for (Scored x : scoredTerritories) {
            Territory t = territories.stream().filter(z -> z.getId().equals(x.id)).findFirst().orElse(null);
            if (t == null) {
                continue;
            }
            UUID ownerId = t.getOwner().getId();
            userScores.merge(ownerId, x.score * 0.6, Double::sum);
            userSignals.computeIfAbsent(ownerId, k -> new Signals()).add(x.s);
        }
        newFollowers.forEach((uid, n) -> {
            userScores.merge(uid, n * W_FOLLOW, Double::sum);
            userSignals.computeIfAbsent(uid, k -> new Signals()).followers += n;
        });

        List<Map.Entry<UUID, Double>> userRanked = new ArrayList<>(userScores.entrySet());
        userRanked.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        int urank = 0;
        for (Map.Entry<UUID, Double> u : userRanked) {
            if (urank >= MAX_ENTRIES || u.getValue() < MIN_SCORE) {
                break;
            }
            urank++;
            Signals s = userSignals.getOrDefault(u.getKey(), new Signals());
            TrendingEntry e = new TrendingEntry(USER, u.getKey());
            e.setScore(BigDecimal.valueOf(u.getValue()).setScale(4, RoundingMode.HALF_UP));
            e.setRank(urank);
            e.setPreviousRank(previous.get(key(USER, u.getKey())));
            e.setViews7d((int) s.views);
            e.setLikes7d((int) s.likes);
            e.setSaves7d((int) s.saves);
            e.setPosts7d((int) s.posts);
            e.setFollowers7d((int) s.followers);
            e.setComputedAt(now);
            entries.add(e);
        }

        // Tam değiştirme: listeden düşen özne kalmasın.
        trendingRepository.deleteAllInBatch();
        trendingRepository.saveAll(entries);
        log.info("Trend sıralaması yenilendi: {} daire, {} kullanıcı", rank, urank);
        return entries.size();
    }

    // ------------------------------------------------------------ okuma

    @Transactional(readOnly = true)
    public List<TrendingItem> territories(int limit) {
        List<TrendingEntry> entries = trendingRepository.findBySubjectTypeOrderByRankAsc(TERRITORY);
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, Territory> byId = new HashMap<>();
        territoryRepository.findAllById(entries.stream().map(TrendingEntry::getSubjectId).toList())
                .forEach(t -> byId.put(t.getId(), t));

        List<TrendingItem> out = new ArrayList<>();
        for (TrendingEntry e : entries) {
            Territory t = byId.get(e.getSubjectId());
            // Bu arada gizlenmiş/süresi dolmuş olabilir — listeden sessizce düşer.
            if (t == null || t.isHidden()
                    || t.getStatus() != com.waydee.territory.domain.TerritoryStatus.ACTIVE) {
                continue;
            }
            out.add(new TrendingItem(
                    TERRITORY, t.getId().toString(), null, t.getName(),
                    "@" + t.getOwner().getUsername(),
                    MediaUrls.of(t.getOwner().getAvatarMediaId()),
                    e.getRank(), e.climb(), e.getScore(),
                    e.getViews7d(), e.getLikes7d(), e.getSaves7d(), e.getPosts7d(), e.getFollowers7d(),
                    reason(e), t.getCenter().getX(), t.getCenter().getY()));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<TrendingItem> users(int limit) {
        List<TrendingEntry> entries = trendingRepository.findBySubjectTypeOrderByRankAsc(USER);
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> byId = new HashMap<>();
        userRepository.findAllById(entries.stream().map(TrendingEntry::getSubjectId).toList())
                .forEach(u -> byId.put(u.getId(), u));

        List<TrendingItem> out = new ArrayList<>();
        for (TrendingEntry e : entries) {
            User u = byId.get(e.getSubjectId());
            /* Gizli hesaplar duyuru şeridinde YER ALMAZ (kendini gizlemiş kişi
               öne çıkarılmaz).
               🔴 17 Ağu 2026 — `hasPublicProfile()` de eklendi: yönetici hesabı
               şeritte çıkıyor, tıklanınca `/users/{id}` 404 veriyor ve kullanıcı
               ana sayfaya düşüyordu (aramadaki hatanın ikizi). */
            if (u == null || u.isPrivateAccount() || !u.hasPublicProfile()) {
                continue;
            }
            out.add(new TrendingItem(
                    USER, u.getId().toString(), u.getUsername(), u.getDisplayName(), "@" + u.getUsername(),
                    MediaUrls.of(u.getAvatarMediaId()),
                    e.getRank(), e.climb(), e.getScore(),
                    e.getViews7d(), e.getLikes7d(), e.getSaves7d(), e.getPosts7d(), e.getFollowers7d(),
                    reason(e), null, null));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    /** Kullanıcıya "neden trend" diye anlatan tek cümle — en güçlü sinyal seçilir. */
    private static String reason(TrendingEntry e) {
        int[] values = {e.getSaves7d(), e.getFollowers7d(), e.getLikes7d(), e.getPosts7d(), e.getViews7d()};
        String[] labels = {
                e.getSaves7d() + " kez kaydedildi",
                e.getFollowers7d() + " yeni takipçi",
                e.getLikes7d() + " yeni beğeni",
                e.getPosts7d() + " yeni gönderi",
                e.getViews7d() + " görüntülenme",
        };
        double[] weights = {W_SAVE, W_FOLLOW, W_LIKE, W_POST, W_VIEW};
        int bestIdx = -1;
        double bestContribution = 0;
        for (int i = 0; i < values.length; i++) {
            double c = values[i] * weights[i];
            if (values[i] > 0 && c > bestContribution) {
                bestContribution = c;
                bestIdx = i;
            }
        }
        return bestIdx >= 0 ? labels[bestIdx] : "Hareketlilik artıyor";
    }

    private static String key(String type, UUID id) {
        return type + ":" + id;
    }

    private static void applyCounts(List<Object[]> rows, Map<UUID, Signals> target,
                                    java.util.function.ObjLongConsumer<Signals> apply) {
        for (Object[] row : rows) {
            Signals s = target.get((UUID) row[0]);
            if (s != null) {
                apply.accept(s, ((Number) row[1]).longValue());
            }
        }
    }

    /** Bir öznenin ham sinyalleri. */
    private static final class Signals {
        long views;
        long likes;
        long saves;
        long posts;
        long followers;
        long freshViews;
        long freshLikes;
        long freshSaves;

        void add(Signals o) {
            views += o.views;
            likes += o.likes;
            saves += o.saves;
            posts += o.posts;
        }

        /**
         * Taze sinyaller ek katsayıyla sayılır: pencere toplamı zaten taze olanı
         * içerdiği için FARK kadar bonus eklenir (çift sayım olmaz).
         */
        double score(boolean newcomer) {
            double base = W_VIEW * views + W_LIKE * likes + W_SAVE * saves
                    + W_POST * posts + W_FOLLOW * followers;
            double freshBonus = (FRESH_MULTIPLIER - 1) *
                    (W_VIEW * freshViews + W_LIKE * freshLikes + W_SAVE * freshSaves);
            double total = base + freshBonus;
            return newcomer ? total * NEWCOMER_MULTIPLIER : total;
        }
    }

    private record Scored(UUID id, double score, Signals s) {
    }
}
