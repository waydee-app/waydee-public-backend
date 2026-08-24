package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.identity.domain.BillingPeriod;
import com.waydee.identity.domain.CreditLedgerEntry;
import com.waydee.identity.domain.CreditReason;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserCredit;
import com.waydee.identity.domain.UserPlan;
import com.waydee.identity.infrastructure.CreditLedgerRepository;
import com.waydee.identity.infrastructure.UserCreditRepository;
import com.waydee.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * <b>Yapay zekâ kredi ekonomisi</b> (V45).
 *
 * <h3>Kurallar — hepsi bir açığı kapatmak için</h3>
 * <ol>
 *   <li><b>Kredi yalnız ücretli üyelikte harcanır.</b> Bakiyesi olsa bile
 *       süresi dolmuş bir hesap üretim yapamaz. Aksi halde bir ay Premium
 *       olup 10.000 kredi alan hesap, üyeliğini bıraktıktan <b>sonra</b> aylarca
 *       ücretsiz üretim yapardı.</li>
 *   <li><b>Yükleme dönem başına tektir</b> ({@code ref_key} UNIQUE). Webhook'lar
 *       "en az bir kez" teslim edilir; aynı yenileme iki kez gelirse ikinci
 *       yükleme <b>veritabanı seviyesinde</b> reddedilir.</li>
 *   <li><b>Bakiye pakete eşitlenir, birikmez</b> ({@link UserCredit#grantPackage}).</li>
 *   <li><b>Harcama koşullu tek UPDATE'tir</b>
 *       ({@link UserCreditRepository#tryDebit}) — eşzamanlı iki istek aynı
 *       bakiyeyi iki kez harcayamaz.</li>
 *   <li><b>İade tektir</b> ({@code refund:<generationId>} anahtarı).</li>
 *   <li><b>Maliyeti istemci belirlemez.</b> Bu servis yalnız "şu kadar düş"
 *       der; kaç olduğunu çağıran modül <b>kendi</b> parametrelerinden
 *       hesaplar (bkz. {@code aistudio} → {@code CreditCost}).</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserRepository userRepository;
    private final UserCreditRepository creditRepository;
    private final CreditLedgerRepository ledgerRepository;

    /**
     * Hoş geldin kredisini yükleyen <b>ayrı bean</b> (18 Ağu 2026).
     *
     * <p>🔴 Neden ayrı sınıf: yükleme kendi transaction'ında ({@code REQUIRES_NEW})
     * koşmalı ki yarışta patlayan tekil kısıt çağıranı (örn. {@link #summary})
     * <b>rollback-only</b> bırakmasın. Aynı sınıf içinden çağrılsaydı Spring
     * proxy'si devreye girmez ve {@code REQUIRES_NEW} <b>hiç uygulanmazdı</b>.
     */
    private final WelcomeCreditGranter welcomeGrant;

    /**
     * Toplu UPDATE sonrası tek nesneyi tazelemek için (bkz. {@link #spend}).
     *
     * <p>⚠️ Repository katmanı bunu veremez: Spring Data'nın tazeleme metodu
     * yok. {@code EntityManager} burada <b>tek bir satır</b> için kullanılıyor,
     * genel bir veri erişim yolu olarak değil.
     */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /* ------------------------------------------------------------------ okuma */

    /**
     * Bakiye özeti. Satırı olmayan kullanıcı için <b>sıfır</b> döner (satır
     * ilk yüklemede açılır — her yeni hesap için boş bir satır yazmak
     * gereksiz).
     */
    @Transactional
    public CreditSummary summary(UUID userId) {
        /* 🔴 Hoş geldin kredisi BURADA verilir (18 Ağu 2026). Kayıt akışına
           bağlanmadı çünkü hak yeni tanımlandı ve BUGÜNKÜ kullanıcıların da
           alması gerekiyor; kayıt kancası yalnız bundan sonrakileri kapsardı.
           Yükleme `ref_key` ile tekil olduğu için her okumada güvenle çağrılır.
           ⚠️ Bu yüzden metot artık `readOnly` DEĞİL. */
        welcomeGrant.ensureFor(userId);

        User user = userRepository.findById(userId).orElse(null);
        UserPlan plan = user == null ? UserPlan.FREE : user.effectivePlan();
        UserCredit credit = creditRepository.findById(userId).orElse(null);
        BillingPeriod period = user != null && user.isPlanActive() ? user.getPlanPeriod() : null;
        long spent = credit == null ? 0L : credit.getSpentTotal();
        int trialLeft = trialRemaining(spent);
        return new CreditSummary(
                credit == null ? 0 : credit.getBalance(),
                packageFor(plan, period),
                plan.name(),
                spent,
                user != null && user.isPlanActive() ? user.getPlanExpiresAt() : null,
                // 🔴 Ücretsiz hesapta stüdyo artık AÇIK — deneme hakkı kaldıysa.
                plan.paid() || trialLeft > 0,
                trialLeft);
    }

    /**
     * <b>Denemeden geriye kalan</b> — ömür boyu harcamadan türetilir.
     *
     * <p>🔴 Ayrı bir sayaç kolonu <b>bilinçli olarak açılmadı</b>: para benzeri
     * ikinci bir sayaç, defterle sapabilecek ikinci bir gerçek demektir.
     * {@code spentTotal} zaten defterin özetidir ve tek gerçek odur.
     *
     * <p>⚠️ Bu, üyeliği bitmiş bir Premium'un artakalan bakiyesini de kapatır:
     * 5.000 kredi harcamış bir hesap FREE'ye düştüğünde kalan hakkı sıfırdır,
     * yani "bir ay Premium ol, sonra bedava üret" yolu açılmaz — sınıf
     * notundaki <b>1. kuralın</b> ücretsiz-deneme hâli.
     */
    private static int trialRemaining(long spentTotal) {
        return (int) Math.max(0L, UserPlan.FREE_WELCOME_CREDITS - spentTotal);
    }

    /** Son hareketler — arayüzdeki "kredi geçmişi" listesi. */
    @Transactional(readOnly = true)
    public List<CreditLedgerEntry> history(UUID userId, int limit) {
        return ledgerRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    /* ---------------------------------------------------------------- yükleme */

    /**
     * <b>Plan dönemi başladı/yenilendi → paketin kredisini yükle.</b>
     *
     * <p>🔴 {@code REQUIRES_NEW}: bu çağrı ödeme akışının <b>içinden</b> gelir.
     * Aynı dönem ikinci kez işlenirse tekil kısıt patlar; o hatanın ödeme
     * transaction'ını da geri almaması için kendi transaction'ında koşar.
     * Kredi yüklenememesi ödemeyi geçersiz kılmaz — üyelik yine verilmelidir.
     *
     * @param expiresAt üyeliğin bitiş anı; <b>tekrar anahtarının parçası</b>,
     *                  yani her dönem için tam bir yükleme yapılır
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void grantForPlan(UUID userId, UserPlan plan, BillingPeriod period, Instant expiresAt) {
        int amount = packageFor(plan, period);
        if (amount <= 0) {
            return;
        }
        // ⚠️ Anahtar bitiş anını içerir: aynı dönemin ikinci webhook'u aynı
        // anahtarı üretir ve buradan sessizce döner.
        String refKey = "plan:%s:%s".formatted(userId, expiresAt == null ? "na" : expiresAt.toEpochMilli());
        if (ledgerRepository.existsByRefKey(refKey)) {
            return;
        }
        try {
            UserCredit credit = creditRepository.findById(userId)
                    .orElseGet(() -> creditRepository.save(new UserCredit(userId)));
            int delta = credit.grantPackage(amount);
            creditRepository.save(credit);
            if (delta != 0) {
                ledgerRepository.save(new CreditLedgerEntry(userId, delta, credit.getBalance(),
                        CreditReason.GRANT_PLAN, plan.name() + " · " + (period == null ? "-" : period.name()), refKey));
            } else {
                // Bakiye zaten pakete eşitti (aynı gün ikinci kez aynı paket).
                // Defter satırı `delta <> 0` kısıtı yüzünden yazılamaz; anahtarı
                // yine de tüketmek için sıfırdan farklı bir kayıt uydurulmaz.
                log.debug("Kredi yüklemesi etkisiz (bakiye zaten {}), defter satırı yazılmadı", amount);
            }
        } catch (DataIntegrityViolationException e) {
            // Yarış: aynı anahtar bir başka thread tarafından yazıldı. Bu bir
            // hata değil, tam olarak kısıtın var oluş sebebi.
            log.info("Kredi yüklemesi zaten yapılmış (ref={})", refKey);
        }
    }

    /** Yönetici/telafi yüklemesi — tekrar anahtarı yoktur, bilinçli olarak tekrarlanabilir. */
    @Transactional
    public int adminAdjust(UUID userId, int amount, String note) {
        if (amount == 0) {
            throw ApiException.badRequest("Miktar sıfır olamaz");
        }
        UserCredit credit = creditRepository.findById(userId)
                .orElseGet(() -> creditRepository.save(new UserCredit(userId)));
        if (credit.getBalance() + amount < 0) {
            throw ApiException.badRequest("Bakiye eksiye düşemez");
        }
        credit.add(amount);
        creditRepository.save(credit);
        ledgerRepository.save(new CreditLedgerEntry(userId, amount, credit.getBalance(),
                CreditReason.ADMIN_ADJUST, note, null));
        return credit.getBalance();
    }

    /* --------------------------------------------------------------- harcama */

    /**
     * <b>Üretim kapısı.</b> Ücretli ve yürürlükte bir üyelik şarttır.
     *
     * <p>Ayrı hata kodu ({@code PLAN_LIMIT_REACHED}) kullanılır ki arayüz
     * "yasak" yerine <b>yükseltme</b> ekranını açsın — projedeki mağaza
     * kapısıyla aynı desen.
     */
    @Transactional
    public void assertCanUseAi(UUID userId) {
        UserPlan plan = userRepository.findById(userId).map(User::effectivePlan).orElse(UserPlan.FREE);
        if (plan.paid()) {
            return;
        }
        /* 🔴 18 Ağu 2026 — kapı ücretsiz hesaba da BİR KEZ açılıyor. Krediyi
           burada da güvence altına alıyoruz: kullanıcı bakiye ekranını hiç
           açmadan doğrudan "Oluştur" diyebilir ve o yolda hakkı olan kredinin
           yüklenmiş olması gerekir. */
        welcomeGrant.ensureFor(userId);
        long spent = creditRepository.findById(userId).map(UserCredit::getSpentTotal).orElse(0L);
        if (trialRemaining(spent) <= 0) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED,
                    "Ücretsiz deneme hakkını kullandın. Yapay zekâ stüdyosunu "
                            + "sürdürmek için Pro ya da Premium'a geç.");
        }
    }

    /**
     * <b>Krediyi düşer</b> — yetmezse {@code 402 INSUFFICIENT_CREDITS}.
     *
     * <p>⚠️ Bu metot yalnız düşer; <b>kapıyı açmaz</b>. Çağıran önce
     * {@link #assertCanUseAi} demek zorundadır. İkisini birleştirmek, kredi
     * düşümünü plan kontrolünden ayrı test edilemez hâle getirirdi.
     *
     * @param refKey defterdeki iş anahtarı ({@code gen:<id>}) — aynı üretim
     *               için ikinci bir düşüm yapılamaz
     * @return düşüm sonrası bakiye
     */
    @Transactional
    public int spend(UUID userId, int cost, String refKey, String note) {
        if (cost <= 0) {
            throw ApiException.badRequest("Geçersiz kredi maliyeti");
        }
        // Satır yoksa bakiye zaten sıfırdır → doğrudan yetersiz.
        UserCredit credit = creditRepository.findById(userId).orElse(null);
        /* 🔴 Ücretsiz hesabın TAVANI deneme hakkıdır, bakiyesi değil (18 Ağu
           2026). Yalnız bakiyeye bakmak, üyeliği bitmiş bir Premium'un
           artakalan 10.000 kredisini FREE olarak harcamasına izin verirdi —
           sınıf notundaki <b>1. kuralın</b> delinmesi. */
        boolean paid = userRepository.findById(userId).map(User::effectivePlan)
                .orElse(UserPlan.FREE).paid();
        if (!paid && cost > trialRemaining(credit == null ? 0L : credit.getSpentTotal())) {
            throw new ApiException(ErrorCode.PLAN_LIMIT_REACHED,
                    "Ücretsiz deneme hakkın bu işleme yetmiyor. Pro ya da Premium'a geç.");
        }
        if (credit == null || creditRepository.tryDebit(userId, cost) == 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_CREDITS,
                    "Kredin yetmiyor. Bu işlem " + cost + " kredi gerektiriyor.");
        }
        /*
         * 🔴 16 Ağu 2026'da ÖLÇÜLEREK YAKALANDI: defterdeki `balance_after`
         * BAYAT yazılıyordu (ilk harcamada 9940 yerine 10000).
         *
         * Sebep: {@code tryDebit} bir <b>toplu JPQL UPDATE</b>'tir ve doğrudan
         * veritabanına gider — <b>birinci seviye önbelleği bilmez</b>. Hemen
         * ardından {@code findById} çağırmak yeni bir SELECT çalıştırmaz, aynı
         * transaction'daki <b>önbellekten</b> eski nesneyi döndürür. Yani
         * "yeniden okudum" sanmak yetmiyor.
         *
         * ⚠️ Çözüm {@code clear()} DEĞİL: persistence context'i temizlemek
         * elimizdeki nesneleri detach eder ve sonraki yazımlar sessizce
         * kaybolur (86. turun logo hatası). Doğrusu <b>tek nesneyi
         * tazelemektir</b>: {@code refresh} veritabanından zorla okur, başka
         * hiçbir şeye dokunmaz.
         *
         * ⚠️ Bakiyenin KENDİSİ hep doğruydu; yanlış olan yalnız defterin
         * kanıt sütunuydu. Yine de para benzeri bir kayıtta "yanlış ama
         * zararsız" diye bir şey yoktur: destek talebini çözen tek sütun bu.
         */
        entityManager.refresh(credit);
        int after = credit.getBalance();
        ledgerRepository.save(new CreditLedgerEntry(userId, -cost, after, CreditReason.SPEND, note, refKey));
        return after;
    }

    /**
     * <b>İade</b> — üretim başarısız olduğunda. <b>En fazla bir kez.</b>
     *
     * <p>Tekillik iki katlıdır: burada {@code existsByRefKey} kontrolü, altta
     * veritabanı kısıtı. Sessizce döner (çağıran bir hata yolundadır; iadenin
     * ikinci kez denenmesi ikinci bir hata üretmemeli).
     */
    @Transactional
    public void refund(UUID userId, int amount, String refKey, String note) {
        if (amount <= 0 || ledgerRepository.existsByRefKey(refKey)) {
            return;
        }
        try {
            UserCredit credit = creditRepository.findById(userId).orElse(null);
            if (credit == null) {
                return;
            }
            // ⚠️ `add` değil `refund`: iade yeni bir hak değil, harcamanın geri
            // alınmasıdır (gerekçe UserCredit.refund başında).
            credit.refund(amount);
            creditRepository.save(credit);
            ledgerRepository.save(new CreditLedgerEntry(userId, amount, credit.getBalance(),
                    CreditReason.REFUND, note, refKey));
        } catch (DataIntegrityViolationException e) {
            log.info("İade zaten yapılmış (ref={})", refKey);
        }
    }

    /* ---------------------------------------------------------------- yardımcı */

    /**
     * Planın <b>o dönem için</b> kredisi.
     *
     * <p>🔴 Yıllık üyelik <b>12 katını</b> alır: fiyat da aylık eşdeğerin 12
     * katı olarak tahsil ediliyor. Sabit tek bir sayı vermek, yıl boyunca ödeme
     * yapan kullanıcıya bir aylık hak vermek olurdu.
     */
    public static int packageFor(UserPlan plan, BillingPeriod period) {
        int monthly = plan.monthlyCredits();
        if (monthly <= 0) {
            return 0;
        }
        return period == BillingPeriod.YEARLY ? monthly * 12 : monthly;
    }

    /**
     * @param balance   şu anki bakiye
     * @param perPeriod bu üyelik döneminin toplam hakkı (ilerleme çubuğu için)
     * @param plan      yürürlükteki plan adı
     * @param spentTotal ömür boyu harcanan
     * @param renewsAt  üyeliğin (dolayısıyla kredinin) yenileneceği an
     * @param aiEnabled yapay zekâ stüdyosu bu hesapta açık mı — ücretsiz
     *                  hesapta {@code trialRemaining > 0} olduğu sürece
     *                  <b>açıktır</b> (18 Ağu 2026)
     * @param trialRemaining ücretsiz deneme hakkından kalan kredi; ücretli
     *                       hesapta anlamı yoktur ama yine de doğru hesaplanır
     *                       (üyelik biterse arayüz aynı sayıyı gösterir)
     */
    public record CreditSummary(int balance, int perPeriod, String plan,
                                long spentTotal, Instant renewsAt, boolean aiEnabled,
                                int trialRemaining) {
    }
}
