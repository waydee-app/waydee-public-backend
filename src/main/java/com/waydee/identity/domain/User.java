package com.waydee.identity.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /**
     * Üyelik planı (V33). Varsayılan <b>FREE</b> — yeni hesap ücretsiz başlar.
     *
     * <p>⚠️ Haklar {@link UserPlan} içinde tanımlıdır; limit sayılarını
     * servislere dağıtmak tanıtım sayfasındaki vaatten sessizce ayrışmaya
     * yol açardı.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 10)
    private UserPlan plan = UserPlan.FREE;

    @Column(name = "plan_since")
    private java.time.Instant planSince;

    /**
     * PRO üyeliğin <b>bitiş anı</b> (V35). FREE hesapta {@code null}.
     *
     * <p>🔴 Üyelik <b>aylıktır</b>; bu kolon olmadan bir kez PRO olan sonsuza
     * kadar PRO kalıyordu (tanıtım sayfası "$10/ay" derken).
     */
    @Column(name = "plan_expires_at")
    private java.time.Instant planExpiresAt;

    /**
     * Faturalama dönemi (V37): {@code MONTHLY | YEARLY}. FREE hesapta boş.
     *
     * <p>⚠️ Bitiş tarihinden geriye çıkarılamaz: yenilemeler birikimli
     * uzattığı için "365 günden fazla kalmış, demek ki yıllık" çıkarımı yanlış
     * sonuç verirdi.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_period", length = 10)
    private BillingPeriod planPeriod;

    /**
     * <b>Ücretsiz mağaza denemesinin kullanıldığı an</b> (V49). Boşsa hak
     * duruyor demektir.
     *
     * <p>🔴 Neden <b>tarih</b>, neden boolean değil: destek "bu kullanıcı
     * denemesini ne zaman harcadı" diye soruyor ve boolean bunu söyleyemez.
     * Aynı gerekçe {@code planSince} kolonunda da geçerliydi.
     *
     * <p>⚠️ Bu kolon <b>mağazanın süresi değildir</b> — mağazanın bitişi
     * {@code territories.expires_at} kolonundadır. Buradaki tek soru "deneme
     * hakkı harcandı mı"; mağaza silinse bile hak geri gelmez, yoksa kullanıcı
     * mağazasını silip yeniden açarak süreyi sonsuza uzatırdı.
     */
    @Column(name = "free_store_used_at")
    private java.time.Instant freeStoreUsedAt;

    /**
     * <b>Kayıt sonrası sorulan "mağazan hangi alanda?" cevabı</b> (V52).
     *
     * <p>🔴 <b>Neden {@code @ManyToOne} DEĞİL, düz {@code UUID}:</b> kategori
     * {@code territory} modülünün varlığıdır. Buraya bir ilişki kurmak
     * {@code identity → territory} bağımlılığı demekti ve o yön
     * <b>çevrim</b> üretir — {@code territory} zaten {@code identity}'yi
     * tanıyor ({@code PlanService}). Bütünlüğü veritabanındaki yabancı anahtar
     * korur, nesne grafiği değil.
     *
     * <p>⚠️ Bu alan mağazanın kategorisi <b>değildir</b>. Popup kayıt anında
     * çıkar, o sırada kullanıcının mağazası yoktur; cevap burada bekler ve
     * mağaza açılınca {@code territories.category_id}'ye <b>tohum</b> olur.
     * Sonrasında ikisi bağımsızdır.
     */
    @Column(name = "store_category_id")
    @Setter
    private UUID storeCategoryId;

    /**
     * Kategori popup'ı gösterildi mi (V52).
     *
     * <p>🔴 {@link #storeCategoryId} tek başına <b>yetmez</b>: "geç" diyen
     * kullanıcıda o alan boş kalır ve popup her açılışta yeniden çıkardı.
     * Sorulmuş olmak ile cevaplanmış olmak farklı iki olaydır.
     */
    @Column(name = "store_category_asked_at")
    private Instant storeCategoryAskedAt;

    @Column(name = "username", nullable = false, length = 30)
    private String username;

    /**
     * Kullanıcı adı <b>sistem tarafından üretildi</b> ve kullanıcı henüz kendi
     * adını seçmedi (Google ile kayıt, V47).
     *
     * <p>⚠️ Bu bilgi giriş yanıtında taşınan geçici bir bayrak DEĞİL, kalıcı
     * bir kolondur: kullanıcı seçim ekranını kapatıp çıkarsa soru bir dahaki
     * girişte yeniden sorulmalı. Oturuma bağlanırsa hesap otomatik üretilmiş
     * bir adla kalıcı olarak kalırdı.
     */
    @Column(name = "username_pending", nullable = false)
    private boolean usernamePending = false;

    /** Son ad değişikliği; {@code null} ise hiç değiştirilmedi (ilk değişiklik serbest). */
    @Column(name = "username_changed_at")
    private Instant usernameChangedAt;

    @Column(name = "email", nullable = false)
    private String email;

    /**
     * ⚠️ <b>NULL olabilir.</b> Google ile açılan hesabın şifresi yoktur. Buraya
     * "nasılsa kullanılmaz" diye rastgele bir hash yazmak yanıltıcı olurdu:
     * şifreli giriş yolu çalışmaya devam eder ve durum sessizce gizlenirdi.
     * Şifre kontrolü yapan her yer {@link #hasPassword()} ile başlamalıdır.
     */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** Hesabın açıldığı yol. Mevcut tüm kayıtlar {@code LOCAL} olarak dolduruldu (V28). */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * Google hesabının <b>değişmeyen</b> kimliği ({@code id_token.sub}).
     *
     * <p>Eşleme e-posta ile değil bununla yapılır: kullanıcı Google hesabının
     * adresini değiştirse bile aynı Waydee hesabına girer.
     */
    @Column(name = "google_sub", length = 64)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Setter
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private UserStatus status;

    @Column(name = "display_name", nullable = false, length = 60)
    @Setter
    private String displayName;

    @Column(name = "bio", length = 280)
    @Setter
    private String bio;

    @Column(name = "avatar_media_id")
    @Setter
    private UUID avatarMediaId;

    /** Gizli hesap: takip için onay gerekir; içerik/listeler yalnız takipçilere açık. */
    @Column(name = "is_private", nullable = false)
    @Setter
    private boolean privateAccount;

    /** Görünüm tercihi: 'light' | 'dark' | null (=sistem tercihi takip edilir). */
    @Column(name = "theme_mode", length = 10)
    @Setter
    private String themeMode;

    /** Harita stili tercihi: 'light' | 'dark' | 'streets' | 'satellite' | null. */
    @Column(name = "map_style", length = 20)
    @Setter
    private String mapStyle;

    /**
     * Arayüz dili: 'tr' | 'en' | 'ar' | 'de' | 'es' | null.
     *
     * <p>⚠️ {@code null} = <b>tarayıcının dilini takip et</b>. Kullanıcı
     * ayarlardan açıkça bir dil seçmediyse cihazının dili kullanılır; seçtiği
     * anda burası dolar ve tarayıcı tercihi artık ezmez.
     */
    @Column(name = "locale", length = 5)
    @Setter
    private String locale;

    /**
     * E-posta adresi doğrulandı mı. Kayıtta {@code false} başlar; doğrulama
     * bağlantısına tıklanınca {@code true} olur.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public User(String username, String email, String passwordHash, String displayName, Role role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Google ile açılan hesap: şifre <b>yoktur</b> ve adres Google tarafından
     * zaten doğrulanmıştır (çağıran {@code email_verified} bayrağını kontrol
     * etmek zorundadır), bu yüzden hesap doğrulanmış başlar — aksi halde
     * kullanıcı hiç almadığı bir doğrulama postasını beklerdi.
     */
    public static User fromGoogle(String username, String email, String displayName, String googleSub) {
        User user = new User();
        user.username = username;
        user.email = email;
        user.passwordHash = null;
        user.displayName = displayName;
        user.role = Role.USER;
        user.status = UserStatus.ACTIVE;
        user.authProvider = AuthProvider.GOOGLE;
        user.googleSub = googleSub;
        /* 🔴 Ad e-posta yerel kısmından TÜRETİLDİ — kullanıcının seçimi değil.
           Arayüz ilk girişte seçim ekranını bu bayrakla açar (V47). */
        user.usernamePending = true;
        user.markEmailVerified();
        return user;
    }

    /**
     * <b>Kullanıcı adını değiştirir</b> (V47).
     *
     * <p>⚠️ Doğrulama (biçim, rezerve, benzersizlik, bekleme süresi) çağıranın
     * işidir — entity yalnız durumu tutarlı bırakır: ad değişince "bekliyor"
     * bayrağı düşer ve zaman damgası yazılır. İkisini ayrı ayrı set eden bir
     * çağıran er geç birini unuturdu.
     */
    public void changeUsername(String newUsername) {
        this.username = newUsername;
        this.usernamePending = false;
        this.usernameChangedAt = Instant.now();
    }

    /**
     * Kullanıcı üretilmiş adı <b>olduğu gibi kabul etti</b> — değiştirmedi ama
     * artık sorulmasın.
     * <p>⚠️ {@code usernameChangedAt} YAZILMAZ: kullanıcı adını değiştirmedi,
     * yalnız onayladı. Yazsaydık gerçek ilk değişikliği 30 gün kilitlerdik.
     */
    public void keepGeneratedUsername() {
        this.usernamePending = false;
    }

    /** Şifreli giriş bu hesap için mümkün mü. */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    /**
     * Mevcut (şifreli) bir hesaba Google hesabını bağlar.
     *
     * <p>⚠️ Çağıran, Google'ın adresi <b>doğruladığını</b> ({@code email_verified})
     * ve adresin bu hesabın adresiyle birebir aynı olduğunu kontrol etmiş
     * olmalıdır. Aksi halde başkasının adresini iddia eden bir Google hesabı
     * mevcut bir Waydee hesabını ele geçirebilirdi.
     */
    public void linkGoogle(String googleSub) {
        this.googleSub = googleSub;
        // Adresin sahipliği Google tarafından kanıtlandı: bekleyen doğrulama varsa kapanır.
        markEmailVerified();
    }

    /**
     * <b>Hesabi anonimlestir</b> (yonetici silmesi).
     *
     * <p>KIRMIZI: satir SILINMEZ. `users` satirina RESTRICT ile bagli mali
     * kayitlar var (`invoices`, `purchases`); gercek DELETE ya hata verir ya da
     * CASCADE ile fatura gecmisini yok eder. Silinen sey <b>kisisel veridir</b>.
     *
     * <p>UYARI: kullanici adi da degistirilir. Birakilsaydi silinen hesabin
     * vitrin adresi (`/kullaniciadi`) hala o kisiyi isaret ediyor gorunurdu ve
     * adres baskasi tarafindan alinamazdi.
     *
     * <p>UYARI: sifre hash'i gecersiz bir degerle ezilir - bcrypt'in
     * uretemeyecegi bir dize, yani hicbir sifre eslesmez. Bos birakmak
     * `matches()` cagrisinda beklenmeyen davranisa yol acabilirdi.
     */
    public void anonymize() {
        String suffix = id.toString().substring(0, 8);
        this.username = "silinmis_" + suffix;
        this.email = "silinmis+" + suffix + "@waydee.local";
        this.displayName = "Silinmiş kullanıcı";
        this.bio = null;
        this.avatarMediaId = null;
        this.passwordHash = "!silinmis";
        this.status = UserStatus.DELETED;
        this.privateAccount = true;
        this.googleSub = null;
        this.emailVerified = false;
        this.emailVerifiedAt = null;
        this.plan = UserPlan.FREE;
        this.planExpiresAt = null;
        this.planPeriod = null;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * Adresi değiştirir ve doğrulamayı <b>sıfırlar</b> — yeni adres kanıtlanana
     * kadar hesap doğrulanmamış sayılır. (Doğrulanmış adres değişimi
     * {@link #changeVerifiedEmail(String)} ile yapılır.)
     */
    public void changeEmail(String newEmail) {
        this.email = newEmail;
        this.emailVerified = false;
        this.emailVerified = false;
        this.emailVerifiedAt = null;
    }

    /**
     * E-posta değişimi akışının sonu: yeni adres zaten bağlantıyla kanıtlandığı
     * için doğrulanmış olarak yazılır.
     */
    public void changeVerifiedEmail(String newEmail) {
        this.email = newEmail;
        markEmailVerified();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        this.emailVerifiedAt = Instant.now();
    }

    public void markLogin() {
        this.lastLoginAt = Instant.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * Planı değiştirir ve tarihini damgalar.
     *
     * <p>⚠️ Tek yol budur (setter yok): plan değişikliğinin ne zaman olduğunu
     * bilmek destek/iade için gerekiyor ve serbest bir setter bunu atlamayı
     * kolaylaştırırdı.
     */
    public void changePlan(UserPlan next) {
        this.plan = next;
        this.planSince = java.time.Instant.now();
        // FREE'ye dönüşte bitiş anı anlamını yitirir; kalırsa "süresi dolmuş
        // PRO" ile "hiç PRO olmamış" ayırt edilemez hâle gelirdi.
        this.planExpiresAt = null;
        this.planPeriod = null;
    }

    /**
     * <b>Ücretli üyeliği bir DÖNEM uzatır</b> (V35; V37'de plan + dönem aldı).
     *
     * <p>⚠️ Uzatma <b>mevcut bitişin üstüne</b> biner: süresi henüz dolmamışken
     * yenileyen kullanıcı kalan günlerini kaybetmemeli. Süresi geçmişse
     * <b>şimdiden</b> başlar (geçmişe ay eklemek, ödediği hâlde hemen dolmuş
     * bir üyelik verirdi).
     *
     * <p>🔴 <b>Plan değişiyorsa birikim yapılmaz.</b> PRO'dan PREMIUM'a geçen
     * kullanıcının kalan PRO günleri yeni planın süresine eklenirse, ucuz planla
     * biriktirilen süre pahalı planda kullanılmış olurdu. Aynı plan yenileniyorsa
     * birikim korunur.
     */
    public void grantPlan(UserPlan next, BillingPeriod period) {
        if (!next.paid()) {
            changePlan(next);
            return;
        }
        Instant now = Instant.now();
        boolean samePlanStillRunning = plan == next
                && planExpiresAt != null
                && planExpiresAt.isAfter(now);
        Instant base = samePlanStillRunning ? planExpiresAt : now;
        this.plan = next;
        this.planPeriod = period;
        this.planSince = now;
        this.planExpiresAt = base.plus(period.length());
    }

    /**
     * <b>Yürürlükteki ücretli üyelik</b> — süresi dolmuş üyelik, üyelik değildir.
     *
     * <p>🔴 Bu kontrol okuma tarafındadır ve <b>süpürmeden bağımsızdır</b>:
     * zamanlanmış iş saat başı koşar, aradaki sürede kullanıcı sınırsız
     * kalmamalı. (Bölge kirasında da aynı iki katlı yapı var.)
     *
     * <p>⚠️ {@code planExpiresAt == null} iken ücretli plan <b>geçersiz</b>
     * sayılır — V35 mevcut satırları doldurdu, yeni üyelikler hep tarihle
     * yazılır; boş kalan bir satır ancak bozuk veridir ve süresiz hak vermemeli.
     */
    public boolean isPlanActive() {
        return plan.paid()
                && planExpiresAt != null
                && planExpiresAt.isAfter(Instant.now());
    }

    /** Sınırların hesabında kullanılan gerçek plan. */
    public UserPlan effectivePlan() {
        return isPlanActive() ? plan : UserPlan.FREE;
    }

    /** Haritada mağaza dairesi açma hakkı — yalnız yürürlükteki PREMIUM. */
    public boolean canOwnStore() {
        return effectivePlan().canOwnStore();
    }

    /**
     * <b>Ücretsiz 1 aylık mağaza denemesi hâlâ duruyor mu?</b>
     *
     * <p>⚠️ Yalnız hakkı sorar, <b>plana bakmaz</b> — Premium bir kullanıcı
     * denemesini hiç harcamamış olabilir ve üyeliği bittiğinde onu kullanabilir.
     * Kapıyı kuran çağrı {@link #canOpenStoreNow()}.
     */
    public boolean freeStoreTrialAvailable() {
        return freeStoreUsedAt == null;
    }

    /**
     * <b>Şu anda mağaza açabilir mi?</b> — plan hakkı <i>veya</i> harcanmamış
     * deneme.
     *
     * <p>🔴 Kapı <b>tek yükleme</b> hâlinde durur: 88. turun dersi (herkese açık
     * bir elemeyi tek yerde yapma, tüm yüzeyleri say) buranın da kuralı. Kapıyı
     * çağıran her yüzey — {@code PlanService}, {@code TerritoryService},
     * oturum yanıtı — aynı cümleyi sorar.
     */
    public boolean canOpenStoreNow() {
        return canOwnStore() || freeStoreTrialAvailable();
    }

    /**
     * <b>Mağaza kurulumu deneme hakkıyla yapıldı</b> — hak tüketildi.
     *
     * <p>⚠️ Yalnız gerçekten deneme kullanıldığında çağrılır. Premium bir
     * kullanıcı mağaza açtığında damgalanmaz; aksi hâlde üyeliği bittiğinde
     * hiç kullanmadığı denemeyi de kaybederdi.
     */
    /**
     * Kategori popup'ı bir kez gösterildi — cevaplansın ya da geçilsin, bir daha çıkmaz.
     *
     * <p>⚠️ Damga <b>idempotenttir</b>: ikinci çağrı ilk sorulma anını
     * ezmez, yoksa "ne zaman soruldu" bilgisi her açılışta bugüne kayardı.
     */
    public void markStoreCategoryAsked() {
        if (storeCategoryAskedAt == null) {
            this.storeCategoryAskedAt = Instant.now();
        }
    }

    /** Popup çıkmalı mı — henüz hiç sorulmadıysa. */
    public boolean needsStoreCategoryPrompt() {
        return storeCategoryAskedAt == null;
    }

    public void markFreeStoreTrialUsed() {
        if (freeStoreUsedAt == null) {
            this.freeStoreUsedAt = Instant.now();
        }
    }

    /**
     * <b>Mavi tik.</b> PRO üyelik doğrulama rozetini de kapsar.
     *
     * <p>🔴 Rozetin kaynağı <b>tek</b>: plan. Ayrı bir {@code verified} kolonu
     * açmak, "kim doğruladı" sorusunu iki yere bölerdi — plan zaten ödeme ya da
     * yönetici kararıyla değişiyor, ikisi de rozeti hak eden kapılar.
     * (Bölgedeki {@code Territory.verified} bundan <b>ayrıdır</b>: o bir yerin
     * kurumsal doğrulamasıdır, kişinin değil.)
     *
     * <p>⚠️ Süresi dolan üyelikte rozet de <b>düşer</b>.
     */
    public boolean hasVerifiedBadge() {
        return isPlanActive();
    }

    /**
     * <b>Bu hesabın herkese açık bir vitrini var mı?</b>
     *
     * <p>🔴 16 Ağustos 2026 — kullanıcı: <i>"admin hesabını, her yönetim
     * hesabının profilini kaldır; {@code /admin} yazınca admin hesabına
     * gidiyor, bunu istemiyorum."</i>
     *
     * <p><b>Yönetim hesabı bir vitrin değildir.</b> {@code /admin} adresi
     * çalıştığı sürece ürün, yöneticinin kullanıcı adını, görünen adını ve
     * avatarını <b>ilan ediyordu</b>; bu hem anlamsız bir sayfa hem de saldırgana
     * bedava bilgi (hangi kullanıcı adına şifre denemesi yapacağını söylüyor).
     *
     * <p>⚠️ Kural <b>ROLE bakar, kullanıcı adına DEĞİL</b>. "admin" adını
     * kara listeye almak yalnız bir hesabı kapatırdı; ikinci bir yönetici
     * eklendiğinde onun vitrini yine açık kalırdı.
     *
     * <p>⚠️ Site haritası bu elemeyi zaten yapıyordu
     * ({@code UserRepository.findIndexableProfiles}) ama <b>adresin kendisi</b>
     * açıktı — yani Google'a söylenmiyor, elle yazan görebiliyordu. Eleme artık
     * tek bir yerden, <b>her</b> herkese açık uçta uygulanır.
     */
    public boolean hasPublicProfile() {
        return status == UserStatus.ACTIVE && role != Role.ADMIN;
    }
}
