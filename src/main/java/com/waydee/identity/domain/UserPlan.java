package com.waydee.identity.domain;

/**
 * Üyelik planı ve <b>hakları</b> (V33, V37'de PREMIUM eklendi).
 *
 * <p>Limitler burada, tek yerde durur — kontrol eden servis bunları okur.
 * Sayıları koda dağıtmak, tanıtım sayfasındaki vaatle arka ucun sessizce
 * ayrışmasına yol açardı.
 *
 * <p>🔴 <b>Mağaza dairesi PREMIUM'a özeldir</b> (V37/V38). Daire çizmek eskiden
 * plandan bağımsız, km² üzerinden ayrı bir alışverişti; artık üyeliğin bir
 * hakkıdır. Kapı {@link #canOwnStore()} üzerinden sorulur — plan adını koda
 * dağıtmak, üçüncü bir plan eklendiğinde her karşılaştırmayı tek tek bulmayı
 * gerektirirdi.
 */
public enum UserPlan {

    /**
     * Ücretsiz: <b>haftada</b> 1 gönderi, 3 ürün etiketi.
     *
     * <p>🔴 <b>Aylık</b> kredisi ve <b>süregelen</b> mağaza hakkı yoktur — ama
     * ikisinin de <b>bir defalık deneme</b> hâli vardır
     * ({@link #FREE_WELCOME_CREDITS}, {@link #FREE_STORE_TRIAL_DAYS}).
     * Deneme bilinçli olarak bu enum'a <b>konmadı</b>: buradaki alanlar
     * "her dönem tekrarlanan hak"tır, deneme ise <b>hesap ömründe bir kez</b>
     * verilir ve kullanıldığı kullanıcı satırında işaretlenir. Aynı alana
     * yazsaydık, süresi dolan her ay denemeyi yeniden dağıtırdık.
     */
    FREE(1, 3, false, 0),

    /** Pro: sınırsız gönderi/etiket + mavi tik. Mağaza dairesi <b>yok</b>. Ayda <b>2.000</b> kredi. */
    PRO(-1, -1, false, 2_000),

    /** Premium: Pro'nun hepsi + haritada <b>100 m mağaza dairesi</b>. Ayda <b>10.000</b> kredi. */
    PREMIUM(-1, -1, true, 10_000);

    /**
     * <b>Ücretsiz denemenin mağaza süresi — 1 ay</b> (18 Ağu 2026, kullanıcı
     * talimatı: "free pakete 1 aylık mağaza açma hakkı vereceğiz").
     *
     * <p>⚠️ Süre <b>üyeliğe değil sabit güne</b> bağlıdır: ücretsiz hesabın
     * bitiş tarihi yoktur ({@code planExpiresAt == null}) ve mevcut
     * {@code storeLeaseDays} böyle bir hesaba <b>1 gün</b> verirdi.
     *
     * <p>⚠️ Hak <b>hesap ömründe bir kezdir</b>. Süresiz olsaydı ücretsiz
     * kullanıcı mağazasını her ay yeniden açıp Premium'un hakkını bedava
     * kullanırdı; işaretleyen kolon {@code users.free_store_used_at}.
     */
    public static final int FREE_STORE_TRIAL_DAYS = 30;

    /**
     * <b>Ücretsiz denemenin yapay zekâ kredisi</b> (18 Ağu 2026, kullanıcı
     * talimatı: "bir defa ai toolu max kullanacak kadar kredi").
     *
     * <p>🔴 Sayı keyfi değil, <b>en pahalı tek üretimin maliyetidir</b>:
     * yüksek kalite + izin verilen en çok ürün
     * ({@code CreditCost.of(true, MAX_PRODUCTS)} = 120 + 3×10). Böylece
     * ücretsiz kullanıcı stüdyoyu <b>tam kapasitede bir kez</b> dener; daha
     * azı, denemenin "yüksek kalite" seçeneğini görünür ama kullanılamaz
     * bırakırdı.
     *
     * <p>⚠️ Değer burada <b>elle</b> yazılıdır çünkü {@code aistudio} modülü
     * {@code identity}'ye bağımlıdır, tersi değil — sabiti oradan almak
     * modüller arası döngü olurdu. Eşitliği {@code CreditCostTest} bekçilik
     * eder: maliyet formülü değişirse test kırılır.
     */
    public static final int FREE_WELCOME_CREDITS = 150;

    private final int weeklyPosts;
    private final int tagsPerPost;
    private final boolean store;
    private final int monthlyCredits;

    UserPlan(int weeklyPosts, int tagsPerPost, boolean store, int monthlyCredits) {
        this.weeklyPosts = weeklyPosts;
        this.tagsPerPost = tagsPerPost;
        this.store = store;
        this.monthlyCredits = monthlyCredits;
    }

    /**
     * <b>Aylık yapay zekâ kredisi</b> (V45, kullanıcı talimatı: "proya 2000
     * premiuma 10000").
     *
     * <p>⚠️ Değer <b>bir aylık</b> haktır. Yıllık üyelik fiyatı da aylık
     * eşdeğerin <b>12 katı</b> olarak tahsil ediliyor
     * ({@code 09-fiyatlandirma-cakisma}); kredi tarafında da aynı çarpanı
     * uygulamamak, yıllık ödeyene bir aylık hak vermek olurdu. Çarpan
     * {@code CreditService.packageFor} içinde <b>dönemden</b> gelir.
     *
     * <p>⚠️ FREE <b>sıfırdır ve öyle kalmalı</b>: ücretsiz hesaba kredi vermek,
     * hesap açıp kredi harcayıp yeni hesap açmayı ("çoklu hesap") bedava bir
     * üretim yoluna çevirirdi.
     */
    public int monthlyCredits() {
        return monthlyCredits;
    }

    /** −1 = sınırsız. <b>Takvim haftası</b> başına hak (bkz. PlanService). */
    public int weeklyPosts() {
        return weeklyPosts;
    }

    public int tagsPerPost() {
        return tagsPerPost;
    }

    public boolean unlimited() {
        return weeklyPosts < 0;
    }

    /** Haritada mağaza dairesi açabilir mi? */
    public boolean canOwnStore() {
        return store;
    }

    /** Ücretli mi — mavi tik ve süre takibi buna bağlı. */
    public boolean paid() {
        return this != FREE;
    }
}
