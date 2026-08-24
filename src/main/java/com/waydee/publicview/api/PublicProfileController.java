package com.waydee.publicview.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.TurnstileService;
import com.waydee.common.security.VisitorPassService;
import com.waydee.common.storage.MediaUrls;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserStatus;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.ProfileContentController;
import com.waydee.social.api.ProfilePostController;
import com.waydee.social.infrastructure.CollectionRepository;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.ProfileLinkRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.waydee.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

/**
 * <b>Vitrin profili</b> — kullanıcının <i>tag</i>'iyle açılan herkese açık sayfası
 * ({@code /{tag}}), referans (taginbio) profiliyle aynı üç sekme: bağlantılar ·
 * gönderiler · koleksiyonlar.
 *
 * <p><b>Tag = kullanıcı adıdır.</b> Kayıt olurken zaten benzersiz bir kullanıcı adı
 * alınıyor; ikinci bir "tag" alanı açmak aynı şeyin iki kaynağı olurdu ve ikisi
 * birbirinden kayabilirdi. Adres bu yüzden doğrudan kullanıcı adıdır.
 *
 * <p>🔒 Kurallar {@code PublicViewController} ile aynıdır ve bilinçli olarak dardır:
 * <ul>
 *   <li><b>Yalnız GET</b> — hiçbir yazma ucu yok.</li>
 *   <li><b>Ziyaretçi geçişi</b> ({@code X-Waydee-Pass}) zorunlu — vitrin kapısı
 *       dekoratif değildir.</li>
 *   <li><b>Gizli hesap</b> ({@code isPrivate}) içerik döndürmez: başlık görünür,
 *       sekmeler <b>403</b>. Aksi halde gizlilik ayarı anlamsız kalırdı.</li>
 *   <li><b>Askıya alınmış hesap 404</b> — varlığı bile sızmaz.</li>
 *   <li>Bağlantılarda yalnız <b>aktif</b> olanlar döner; e-posta hiçbir yanıtta yok.</li>
 * </ul>
 */
@Tag(name = "Public profile", description = "Tag ile açılan herkese açık profil")
@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicProfileController {

    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 48;

    private final UserRepository userRepository;
    private final ProfileLinkRepository linkRepository;
    private final PostRepository postRepository;
    private final CollectionRepository collectionRepository;
    private final com.waydee.identity.application.SocialLinkService socialLinkService;
    private final VisitorPassService visitorPassService;
    private final TurnstileService turnstileService;
    private final com.waydee.identity.application.FollowService followService;
    private final com.waydee.identity.infrastructure.FollowRepository followRepository;
    /*
     * 🔴 `NotificationService` KALDIRILDI: profil ziyareti artık doğrudan
     * bildirilmiyor, ÖLÇÜLÜYOR — bildirimi de ölçümü yapan servis üretiyor
     * (bkz. `recordProfileView`). İkisini burada da tutmak, aynı ziyaret için
     * iki ayrı çağrı yolu bırakırdı.
     */
    private final com.waydee.social.application.AnalyticsService analyticsService;

    @Operation(summary = "Tag ile profil başlığı")
    @GetMapping("/{tag}")
    public PublicProfileResponse profile(@PathVariable String tag, HttpServletRequest http,
                                        @AuthenticationPrincipal AuthenticatedUser viewer) {
        requirePass(http, viewer);
        User u = visible(tag);
        /*
         * 🔴 9 Ağu 2026 — sayaçlar ve sosyal ikonlar artık "gizli mi?" değil,
         * "BU KİŞİ GÖREBİLİR Mİ?" sorusuna göre doldurulur.
         *
         * Önceden gizli hesapta hepsi 0 dönüyordu ve arayüz sekmeleri
         * <b>sayaçlardan</b> türettiği için, kabul edilmiş bir takipçi sekmeleri
         * hiç göremiyordu — içerik ucu açılsa bile ekran boş kalırdı.
         * (Kullanıcının bildirdiği hata: `app.waydee.com/yasinventures`.)
         */
        boolean canView = !u.isPrivateAccount()
                || (viewer != null && followService.canViewContent(viewer.id(), u.getId()));

        /*
         * 🔴 18 Ağu 2026 — "profilini görüntüledi" bildirimi BURADA doğar.
         * Daha önce yalnız daire profilinde ({@code /t/<id>}) üretiliyordu;
         * oysa kullanıcıların gerçekte gezdiği yer burası.
         *
         * ⚠️ Yalnız OTURUM AÇMIŞ ziyaretçi: anonim bir ziyaretin bildirimi
         * "kim baktı" sorusuna cevap veremez, yalnız gürültü olurdu.
         * ⚠️ Kısma ve kendi profiline bakma kontrolü servisin içinde.
         * ⚠️ Yazım bu GET'i düşürmez: çağrı `@Async` ve içindeki kısıt ihlali
         * yutulur.
         */
        if (viewer != null) {
            /*
             * 🔴 21 Ağu 2026 — ARTIK ÖLÇÜLÜYOR, yalnız bildirilmiyor.
             *
             * Eskiden burası doğrudan `notifyProfileView` çağırıyordu: kullanıcı
             * *"X bölgeni görüntüledi"* bildirimi alıyor ama raporunda
             * <b>0 görüntülenme</b> görüyordu — ziyaret hiçbir yere yazılmıyordu.
             * `recordProfileView` ziyareti sahibinin mağazasına kaydeder (bu
             * üründe mağaza profilin kendisidir).
             *
             * ⚠️ 24 Ağu 2026 — BİLDİRİM ARTIK ÜRETİLMİYOR (kullanıcı isteği);
             * ziyaret yalnız ölçülür ve raporda görünür. Ayrıca aynı kişi
             * aynı gün <b>bir kez</b> sayılır (V54 tekil indeksi).
             */
            analyticsService.recordProfileView(u.getId(), viewer.id());
        }

        return new PublicProfileResponse(
                u.getId(), u.getUsername(), u.getDisplayName(), u.getBio(),
                u.getAvatarMediaId() == null ? null : MediaUrls.of(u.getAvatarMediaId()),
                // ⚠️ `privateAccount` bayrağı gerçeği söyler ama arayüz artık
                // "kilitli ekran" kararını `canViewContent` ile verir.
                u.isPrivateAccount(),
                u.hasVerifiedBadge(),
                // Sekme rozetlerindeki sayılar — referansta sekme başlığının
                // yanında bir çip olarak duruyor.
                canView ? (int) linkRepository.countByOwnerId(u.getId()) : 0,
                canView ? (int) postRepository.countProfilePosts(u.getId()) : 0,
                canView ? (int) collectionRepository.countByOwnerId(u.getId()) : 0,
                /* Sosyal hesaplar profil BASLIGINDA cizilir (referans:
                   taginbio.com/angnxyy62 - kullanici adinin hemen altinda dort
                   ikon). Ayri bir uc yerine bu yanita gomuldu: baslik zaten tek
                   istekle geliyor, ikinci cagri ikonlarin gec belirmesine ve
                   basligin zipllamasina yol acardi.
                   UYARI: goremeyene BOS doner - baglantilar da icerik. */
                canView ? socialLinkService.list(u.getId()) : java.util.List.of(),
                canView,
                /*
                 * 🔴 18 Ağu 2026 — TAKİPÇİ SAYAÇLARI EKLENDİ.
                 *
                 * Vitrin profili ({@code waydee.com/<kullanıcı>}) takipçi ve
                 * takip sayısını <b>hiç göstermiyordu</b>; oysa oturum içindeki
                 * profil gösteriyordu. Ziyaretçi, bir hesabın ne kadar
                 * izlendiğini göremeden karar veriyordu.
                 *
                 * ⚠️ Sayaçlar {@code canView} kapısına BAĞLANMAZ — bilerek.
                 * Takipçi sayısı gizli hesapta da <b>görünür</b> olmalıdır:
                 * Instagram dâhil her platformda böyledir ve "takip et"
                 * kararını veren şey tam olarak bu sayıdır. Gizlenen şey
                 * içeriktir, hesabın büyüklüğü değil. (İçerik sayaçları —
                 * gönderi/bağlantı/koleksiyon — kapıda kalmaya devam eder.)
                 */
                (int) followRepository.countByFolloweeIdAndStatus(
                        u.getId(), com.waydee.identity.domain.FollowStatus.ACCEPTED),
                (int) followRepository.countByFollowerIdAndStatus(
                        u.getId(), com.waydee.identity.domain.FollowStatus.ACCEPTED));
    }

    @Operation(summary = "Profilin bağlantıları (sayfalı)")
    @GetMapping("/{tag}/links")
    public PageResponse<ProfileContentController.LinkResponse> links(
            @PathVariable String tag, HttpServletRequest http,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        requirePass(http, viewer);
        User u = openProfile(tag, viewer);
        return PageResponse.from(
                linkRepository.findByOwnerIdAndActiveTrueOrderByPositionAsc(u.getId(), pageable(page, size)),
                ProfileContentController.LinkResponse::from);
    }

    @Operation(summary = "Profilin gönderileri (sayfalı)")
    @GetMapping("/{tag}/posts")
    public PageResponse<ProfilePostController.PostTile> posts(
            @PathVariable String tag, HttpServletRequest http,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        requirePass(http, viewer);
        User u = openProfile(tag, viewer);
        return PageResponse.from(
                postRepository.findProfilePosts(u.getId(), pageable(page, size)),
                ProfilePostController.PostTile::from);
    }

    @Operation(summary = "Profilin koleksiyonları (sayfalı)")
    @GetMapping("/{tag}/collections")
    public PageResponse<ProfileContentController.CollectionResponse> collections(
            @PathVariable String tag, HttpServletRequest http,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        requirePass(http, viewer);
        User u = openProfile(tag, viewer);
        return PageResponse.from(
                collectionRepository.findByOwnerIdOrderByPositionAsc(u.getId(), pageable(page, size)),
                ProfileContentController.CollectionResponse::from);
    }

    /**
     * <b>Vitrin avatarının kalıcı adresi</b> — sosyal kart ve arama motoru için.
     *
     * <p>🔴 İmzalı medya bağlantısı 7 günde ölür; bir profil adresi ise arama
     * sonuçlarında aylarca yaşar. Kartın içine imzalı bağlantı gömmek, iki hafta
     * sonra <b>görselsiz</b> bir önizleme demekti. Bu uç her istekte taze imzaya
     * <b>302</b> yönlendirir.
     *
     * <p>⚠️ Ziyaretçi geçişi istemez: çağıran taraf Googlebot / WhatsApp'tır,
     * hiçbiri Turnstile çözemez. Yalnız <b>açık</b> hesabın avatarına yönlendirir.
     */
    @Operation(summary = "Vitrin avatarı (kalıcı adres)")
    @GetMapping("/id/{userId}/avatar")
    public org.springframework.http.ResponseEntity<Void> avatar(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.isPrivateAccount())
                .map(User::getAvatarMediaId)
                .map(id -> org.springframework.http.ResponseEntity
                        .status(org.springframework.http.HttpStatus.FOUND)
                        .location(java.net.URI.create(MediaUrls.of(id)))
                        .<Void>build())
                .orElseGet(() -> org.springframework.http.ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------ yardımcılar
    /**
     * Vitrin kapısı — {@code PublicViewController} ile <b>birebir aynı</b> mantık.
     *
     * <p>⚠️ Turnstile kapalıyken (offline geliştirme) geçiş istenmez; açıkken
     * eksik/bozuk geçiş {@code VISITOR_PASS_REQUIRED} ile reddedilir — ayrı kod
     * şart, çünkü istemci bunu "gizli hesap" 403'ünden ayırıp kapıyı yeniden açar.
     */
    private void requirePass(HttpServletRequest http, AuthenticatedUser viewer) {
        // ⚠️ Oturum varsa ziyaretçi kapısı aranmaz: geçiş ZİYARETÇİ içindir ve
        // giriş yapmış kullanıcının elinde hiç olmaz (kapıdan geçmemiştir).
        if (viewer != null || !turnstileService.isEnabled()) {
            return;
        }
        if (!visitorPassService.isValid(http.getHeader(PublicViewController.PASS_HEADER))) {
            throw new ApiException(ErrorCode.VISITOR_PASS_REQUIRED, "Ziyaretçi doğrulaması gerekli");
        }
    }

    /**
     * Hesabın kendisi görünür mü — askıya alınmış hesap <b>yok</b> sayılır.
     *
     * <p>🔴 16 Ağu 2026 — <b>yönetim hesapları da yok sayılır</b>
     * ({@code hasPublicProfile}). {@code /admin} adresi çalışıyor ve yöneticinin
     * adını, avatarını herkese açıyordu. 404 döner, 403 değil: bir yönetim
     * hesabının <b>varlığı</b> bile sızmamalı.
     */
    private User visible(String tag) {
        return userRepository.findByUsername(tag.trim().toLowerCase(Locale.ROOT))
                .filter(User::hasPublicProfile)
                .orElseThrow(() -> ApiException.notFound("Profil bulunamadı"));
    }

    /**
     * İçerik döndürmeden önceki kapı: gizli hesabın sekmeleri <b>403</b>.
     *
     * <p>⚠️ 404 değil 403: hesabın var olduğu başlık ucundan zaten görülüyor;
     * burada gizlenen şey <b>içeriktir</b>, hesabın varlığı değil.
     *
     * <p>🔴 <b>9 Ağu 2026 — KABUL EDİLMİŞ TAKİPÇİ ARTIK GEÇER.</b> Önceden bu
     * kapı kimliğe hiç bakmıyordu: gizli bir hesabı takip eden (ve isteği
     * <b>kabul edilmiş</b>) kullanıcı bile gönderileri göremiyordu. Yani
     * "gizli hesap" ayarı takip etmeyi <b>anlamsız</b> kılıyordu — istek
     * gönder, kabul edilsin, yine de hiçbir şey görme. Gizliliğin amacı
     * içeriği <b>yabancıdan</b> saklamaktır, takipçiden değil.
     *
     * <p>⚠️ Sahibi de geçer: kendi vitrinini kendi adresinden açtığında
     * "bu hesap gizli" görmek anlamsızdı.
     */
    private User openProfile(String tag, AuthenticatedUser viewer) {
        User u = visible(tag);
        if (!u.isPrivateAccount()) {
            return u;
        }
        if (viewer != null
                && (viewer.id().equals(u.getId()) || followService.canViewContent(viewer.id(), u.getId()))) {
            return u;
        }
        throw ApiException.forbidden("Bu hesap gizli");
    }

    private static Pageable pageable(int page, int size) {
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), s);
    }

    /** {@code verified}: mavi tik — PRO üyelikle gelir; plan adı sızdırılmaz. */
    /**
     * @param canViewContent İçerik bu ziyaretçiye açık mı? Gizli hesapta yalnız
     *        <b>sahibi</b> ve <b>kabul edilmiş takipçi</b> için {@code true}.
     *        Arayüz "bu hesap gizli" ekranını bu alana bakarak çizer —
     *        {@code privateAccount}'a bakarsa takipçiyi de dışarıda bırakır.
     */
    public record PublicProfileResponse(UUID id, String tag, String displayName, String bio,
                                        String avatarUrl, boolean privateAccount, boolean verified,
                                        int linkCount, int postCount, int collectionCount,
                                        /** Profil basligindaki sosyal medya ikonlari. */
                                        java.util.List<com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView> socialLinks,
                                        boolean canViewContent,
                                        /**
                                         * Takipçi/takip sayısı (18 Ağu 2026).
                                         * ⚠️ {@code canViewContent} kapısına TAKILMAZ: hesabın
                                         * büyüklüğü içerik değildir ve "takip et" kararını veren
                                         * şey budur. Bkz. yukarıdaki gerekçe.
                                         */
                                        int followerCount, int followingCount) {
    }
}
