package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserPlan;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.infrastructure.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * <b>Plan hakları kapısı.</b>
 *
 * <p>Tanıtım sayfasının vaadi burada koda dönüşür: <b>Ücretsiz</b> <b>haftada</b>
 * <b>1 gönderi</b> ve gönderi başına <b>3 ürün etiketi</b>; <b>Pro</b> sınırsız.
 *
 * <p>🔒 Kapı <b>sunucudadır</b>. İstemcinin düğmeyi gizlemesi güvenlik değildir —
 * projenin moderasyon ve e-posta doğrulama kapılarıyla aynı ilke. Aksi halde
 * ücretsiz bir hesap doğrudan API'ye istek atıp sınırsız gönderi açabilirdi.
 *
 * <p>🔴 <b>10 Ağustos 2026 — sınır GÜNLÜKTEN HAFTALIĞA çekildi</b> (kullanıcı
 * talimatı). Ücretsiz plan artık günde değil <b>haftada</b> 1 gönderi verir.
 * Alan adları da bunu söyler ({@code weeklyPosts}, {@code postsThisWeek}) —
 * eski {@code daily*} adları bırakılsaydı kod bir şey, ürün başka bir şey
 * anlatırdı.
 *
 * <p>⚠️ Sınır <b>son 7 gün</b> olarak değil, <b>takvim haftası</b> olarak
 * ölçülür (UTC, <b>pazartesi</b> başlangıçlı — ISO-8601). "Son 7 gün" olsaydı
 * kullanıcı her hafta bir öncekinden biraz geç paylaşmak zorunda kalır, kota
 * kayan bir hedefe dönerdi. Bu, günlük sınırdaki ilkeyle aynıdır; yalnız
 * pencere büyüdü.
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    /**
     * {@code FREE_WEEKLY_POSTS} — ücretsiz planın haftalık gönderi tavanı.
     * Verilmezse {@link UserPlan#FREE}'deki değer geçerli; {@code -1} sınırsız.
     */
    @org.springframework.beans.factory.annotation.Value("${waydee.plans.free-weekly-posts:#{null}}")
    private Integer freeWeeklyPosts;

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    /**
     * Kullanıcının <b>yürürlükteki</b> planı.
     *
     * <p>🔴 {@code getPlan()} DEĞİL {@code effectivePlan()}: üyelik aylıktır ve
     * süresi dolmuş bir PRO satırı hâlâ {@code plan = 'PRO'} taşır. Süpürme
     * saat başı koştuğu için arada kalan sürede sınırlar yine uygulanmalı.
     */
    @Transactional(readOnly = true)
    public UserPlan planOf(UUID userId) {
        return userRepository.findById(userId).map(User::effectivePlan).orElse(UserPlan.FREE);
    }

    /** Üyeliğin bitiş anı — arayüz "şu tarihe kadar" yazabilsin diye. */
    @Transactional(readOnly = true)
    public java.time.Instant expiresAt(UUID userId) {
        return userRepository.findById(userId)
                .filter(User::isPlanActive)
                .map(User::getPlanExpiresAt)
                .orElse(null);
    }

    /**
     * <b>Mağaza kapısı.</b> Haritada daire açmak yalnız yürürlükteki PREMIUM'un
     * hakkıdır (V37/V38).
     *
     * <p>🔒 Kapı sunucudadır: istemcinin düğmeyi gizlemesi güvenlik değildir.
     * Ayrı hata kodu ({@code PLAN_LIMIT_REACHED}) kullanılır ki arayüz "yasak"
     * yerine <b>yükseltme</b> ekranını açsın.
     */
    @Transactional(readOnly = true)
    public void assertCanOwnStore(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.canOpenStoreNow()) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED,
                    "Haritada mağaza açmak Premium üyeliğe özeldir. Premium'a geçerek mağazanı oluştur.");
        }
    }

    /**
     * <b>Ücretsiz 1 aylık mağaza denemesi bu kullanıcıda duruyor mu?</b>
     *
     * <p>🔴 18 Ağu 2026. Deneme hakkı <b>plandan bağımsızdır</b> — bu yüzden
     * {@code planOf(...).canOwnStore()} sormak yetmez, kullanıcı satırına
     * bakmak gerekir.
     *
     * <p>⚠️ Arayüz "mağazanı aç" davetini <b>buna</b> bakarak çizer; plan
     * adına bakarak çizerse Premium olmayan herkese Premium duvarını gösterir.
     */
    @Transactional(readOnly = true)
    public boolean storeTrialAvailable(UUID userId) {
        return userRepository.findById(userId).map(User::freeStoreTrialAvailable).orElse(false);
    }

    /** Bu <b>takvim haftasında</b> açılmış gönderi sayısı (silinmişler sayılmaz). */
    @Transactional(readOnly = true)
    public long postsThisWeek(UUID userId) {
        return postRepository.countByAuthorIdAndDeletedAtIsNullAndCreatedAtAfter(userId, startOfWeek());
    }

    /**
     * Yeni gönderi açılabilir mi? Aşılıyorsa <b>403 PLAN_LIMIT_REACHED</b>.
     *
     * <p>⚠️ Ayrı hata kodu şart: istemci bunu "yasak" ile karıştırmayıp
     * <b>yükseltme</b> ekranını açsın diye.
     */
    @Transactional(readOnly = true)
    public void assertCanCreatePost(UUID userId) {
        UserPlan plan = planOf(userId);
        if (plan.unlimited()) {
            return;
        }
        int limit = weeklyPostLimit(plan);
        if (limit < 0) {
            return;
        }
        if (postsThisWeek(userId) >= limit) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED,
                    "Ücretsiz planda haftada " + limit
                            + " gönderi paylaşabilirsin. Pro'ya geçerek sınırsız paylaş.");
        }
    }

    /**
     * <b>Ücretsiz planın haftalık gönderi tavanı — ortamdan verilebilir.</b>
     *
     * <p>🔴 17 Ağu 2026. Değer {@link UserPlan} enum'una gömülüydü, yani
     * değiştirmek <b>kod değişikliği + derleme + dağıtım</b> demekti. Oysa bu
     * bir <b>iş kararıdır</b> ve ürün büyürken ayarlanması gerekir: ölçüldü,
     * 32 kayıtlı kullanıcıya karşılık toplam <b>1</b> gönderi vardı — ilk
     * gönderisini atan herkes duvara çarpıyor ve bunu bir arıza sanıyordu.
     *
     * <p>⚠️ Varsayılan <b>enum'daki değerin kendisi</b>: bu değişiklik
     * davranışı tek başına DEĞİŞTİRMEZ. Tavanı büyütmek ya da kaldırmak
     * ({@code -1} = sınırsız) artık tek bir ortam değişkeni.
     */
    private int weeklyPostLimit(UserPlan plan) {
        return plan == UserPlan.FREE && freeWeeklyPosts != null ? freeWeeklyPosts : plan.weeklyPosts();
    }

    /** Bir gönderiye kaç etiket eklenebilir? Aşılıyorsa 403. */
    @Transactional(readOnly = true)
    public void assertTagCount(UUID userId, int tagCount) {
        UserPlan plan = planOf(userId);
        if (plan.unlimited()) {
            return;
        }
        if (tagCount > plan.tagsPerPost()) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED,
                    "Ücretsiz planda fotoğraf başına " + plan.tagsPerPost()
                            + " ürün etiketi ekleyebilirsin. Pro'ya geçerek sınırsız ekle.");
        }
    }

    /** Arayüzün rozet/sayaç çizebilmesi için tek çağrıda özet. */
    @Transactional(readOnly = true)
    public PlanUsage usage(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        UserPlan plan = user == null ? UserPlan.FREE : user.effectivePlan();
        long used = plan.unlimited() ? 0 : postsThisWeek(userId);
        boolean active = user != null && user.isPlanActive();
        int limit = weeklyPostLimit(plan);
        return new PlanUsage(
                plan.name(),
                limit,
                plan.tagsPerPost(),
                used,
                plan.unlimited() || limit < 0 ? -1 : Math.max(limit - used, 0),
                active ? user.getPlanExpiresAt() : null,
                active && user.getPlanPeriod() != null ? user.getPlanPeriod().name() : null,
                plan.canOwnStore(),
                user != null && user.freeStoreTrialAvailable(),
                UserPlan.FREE_STORE_TRIAL_DAYS);
    }

    /**
     * Süresi dolan PRO hesapları FREE'ye düşürür (saatlik süpürme çağırır).
     *
     * <p>Okuma tarafı zaten süresi geçmişi FREE sayıyor; bu iş satırı da
     * gerçeğe çevirir ki yönetim listesi, raporlar ve mavi tik <b>aynı şeyi</b>
     * göstersin. (Bölge kirasındaki {@code expireLapsedLeases} ile aynı ilke.)
     *
     * @return düşürülen hesap sayısı
     */
    @Transactional
    public int expireLapsedPlans() {
        var lapsed = userRepository.findByPlanInAndPlanExpiresAtBefore(PAID_PLANS, Instant.now());
        lapsed.forEach(u -> u.changePlan(UserPlan.FREE));
        return lapsed.size();
    }

    /** Süre takibi olan planlar — yeni ücretli plan eklenirse buraya girer. */
    private static final java.util.Set<UserPlan> PAID_PLANS =
            java.util.EnumSet.of(UserPlan.PRO, UserPlan.PREMIUM);

    /**
     * İçinde bulunduğumuz <b>ISO haftasının</b> başı: <b>UTC pazartesi 00:00</b>.
     *
     * <p>⚠️ Yerel saat dilimi bilinçli olarak kullanılmaz — kota sunucuda tek
     * bir takvime göre sayılır, yoksa aynı hesap iki cihazda iki farklı hafta
     * görürdü. (Günlük sınırdaki UTC tercihiyle aynı.)
     */
    private static Instant startOfWeek() {
        java.time.LocalDate today = java.time.LocalDate.ofInstant(Instant.now(), java.time.ZoneOffset.UTC);
        java.time.LocalDate monday = today.with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return monday.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }

    /**
     * {@code weeklyPosts}/{@code tagsPerPost} <b>−1 ise sınırsız</b>.
     *
     * <p>🔴 Alanlar 10 Ağu 2026'da {@code dailyPosts / postsToday /
     * postsLeftToday} adlarından çevrildi; sınır <b>haftalık</b>. Arayüz de
     * aynı turda güncellendi — eski adı bekleyen bir istemci kalmadı.
     *
     * @param expiresAt üyeliğin bitiş anı; FREE hesapta {@code null}
     * @param period    MONTHLY | YEARLY; FREE hesapta {@code null}
     * @param canOwnStore haritada mağaza dairesi açabilir mi (yalnız PREMIUM)
     * @param storeTrialAvailable ücretsiz <b>1 aylık</b> mağaza denemesi hâlâ
     *                            duruyor mu (18 Ağu 2026). Plandan bağımsızdır:
     *                            {@code canOwnStore=false} iken bile
     *                            {@code true} olabilir ve arayüzün daveti
     *                            <b>buna</b> bakar.
     * @param storeTrialDays denemenin gün sayısı — arayüz "30 gün" metnini
     *                       kendi yazmasın diye sunucudan gelir
     */
    public record PlanUsage(String plan, int weeklyPosts, int tagsPerPost,
                            long postsThisWeek, long postsLeftThisWeek,
                            Instant expiresAt, String period, boolean canOwnStore,
                            boolean storeTrialAvailable, int storeTrialDays) {
    }
}
