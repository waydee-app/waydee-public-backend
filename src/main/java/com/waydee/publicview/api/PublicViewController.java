package com.waydee.publicview.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.TurnstileService;
import com.waydee.common.security.VisitorPassService;
import com.waydee.geo.application.RegionQueryService;
import com.waydee.identity.api.dto.AuthDtos.PublicUserResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.dto.SocialDtos.ProfileResponse;
import com.waydee.social.application.TerritoryProfileService;
import com.waydee.territory.api.dto.TerritoryDtos.TerritoryResponse;
import com.waydee.territory.application.TerritoryService;
import com.waydee.territory.domain.Territory;
import com.waydee.traffic.application.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Vitrin (landing) uçları — kimliksiz erişilebilen TEK okuma yüzeyi.</b>
 *
 * <p>Projenin genel duruşu "API tamamen kapalı"dır; burası bilinçli ve dar bir
 * istisnadır: ziyaretçi haritada gezebilsin ve <b>yalnız açık hesapların</b>
 * profillerini görebilsin diye açıldı. Kurallar:
 * <ul>
 *   <li>Sadece <b>GET</b>; hiçbir yazma ucu yok.</li>
 *   <li>Gizli hesapların sahip kimliği haritada maskelenir, profilleri 403 döner.</li>
 *   <li>Gizlenmiş (admin) ve pasif bölgeler hiç görünmez.</li>
 *   <li>E-posta, oturum, istatistik gibi hiçbir hassas alan dönmez.</li>
 * </ul>
 * Rate limit filtresi bu yola da uygulanır (bkz. RateLimitFilter).
 */
@Tag(name = "Public", description = "Kimliksiz vitrin: harita + açık profiller")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicViewController {

    /** Ziyaretçi geçişinin taşındığı başlık (bkz. VisitorPassService). */
    public static final String PASS_HEADER = "X-Waydee-Pass";

    private final TerritoryService territoryService;
    private final RegionQueryService regionQueryService;
    private final TerritoryProfileService profileService;
    private final com.waydee.social.application.TerritoryCardService cardService;
    private final com.waydee.social.application.PostService postService;
    private final UserRepository userRepository;
    private final TurnstileService turnstileService;
    private final VisitorPassService visitorPassService;
    private final ClientInfo clientInfo;

    /**
     * Vitrin kapısı: Turnstile jetonu doğrulanır ve kısa ömürlü ziyaretçi geçişi verilir.
     * Turnstile kapalıyken doğrudan geçiş döner (offline geliştirme).
     */
    @Operation(summary = "Ziyaretçi doğrulaması → kısa ömürlü geçiş")
    @PostMapping("/verify")
    public VisitorPassResponse verify(@RequestBody(required = false) VerifyRequest request,
                                      HttpServletRequest http) {
        turnstileService.verify(request != null ? request.turnstileToken() : null, clientInfo.ip(http));
        return new VisitorPassResponse(visitorPassService.issue(), VisitorPassService.TTL.toSeconds());
    }

    /** Doğrulamanın gerekip gerekmediğini istemci açılışta sorar. */
    @Operation(summary = "Vitrin kapısı gerekli mi?")
    @GetMapping("/gate")
    public GateResponse gate() {
        return new GateResponse(turnstileService.isEnabled());
    }

    @Operation(summary = "Harita: sahipli alanlar (gizli hesaplar maskeli)")
    @GetMapping("/map/territories")
    public Map<String, Object> territories(HttpServletRequest http) {
        requireVisitorPass(http);
        return territoryService.publicTerritoriesAsGeoJson();
    }

    @Operation(summary = "Harita: bölgeler ve fiyatlar")
    @GetMapping("/map/regions")
    public Map<String, Object> regions(HttpServletRequest http) {
        requireVisitorPass(http);
        return regionQueryService.regionsAsGeoJson();
    }

    @Operation(summary = "Bölge özeti (açık hesap)")
    @GetMapping("/territories/{territoryId}")
    public TerritoryResponse territory(@PathVariable UUID territoryId, HttpServletRequest http) {
        requireVisitorPass(http);
        requirePublicOwner(territoryId);
        return territoryService.get(territoryId);
    }

    @Operation(summary = "Bölge kartı (açık hesap) — vitrin haritasının zengin kartı")
    @GetMapping("/territories/{territoryId}/card")
    public com.waydee.social.api.dto.TerritoryCardDtos.TerritoryCardResponse card(
            @PathVariable UUID territoryId, HttpServletRequest http) {
        requireVisitorPass(http);
        requirePublicOwner(territoryId);
        // viewerId = null → gizli hesap zaten `requirePublicOwner` ile elenir;
        // kartta da takip/engel durumu üretilmez.
        return cardService.card(territoryId, null);
    }

    @Operation(summary = "Bölge profili (açık hesap) — web sitesi/HTML dahil")
    @GetMapping("/territories/{territoryId}/profile")
    public ProfileResponse profile(@PathVariable UUID territoryId, HttpServletRequest http) {
        requireVisitorPass(http);
        requirePublicOwner(territoryId);
        // viewerId = null → ContentAccessService gizli hesapta zaten 403 verir.
        return profileService.get(territoryId, null);
    }

    /**
     * Bölgenin paylaşımları — harita kartındaki şerit bunu okur.
     *
     * <p>⚠️ Kart artık istatistik ızgarası yerine <b>paylaşımları</b> gösteriyor;
     * vitrinde de aynı kart kullanıldığı için kimliksiz bir uç gerekti. Gizli
     * hesap {@code requirePublicOwner} ile zaten elenir, viewer {@code null}
     * geçtiği için beğeni/oy gibi kişiye özel alanlar üretilmez.
     */
    @Operation(summary = "Bölgenin paylaşımları (açık hesap)")
    @GetMapping("/territories/{territoryId}/posts")
    public com.waydee.common.web.PageResponse<com.waydee.social.api.dto.SocialDtos.PostResponse> posts(
            @PathVariable UUID territoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            HttpServletRequest http) {
        requireVisitorPass(http);
        requirePublicOwner(territoryId);
        return postService.list(territoryId, null, page, Math.min(size, 20));
    }

    @Operation(summary = "Kullanıcı profili (yalnız açık hesap)")
    @GetMapping("/users/{userId}")
    public PublicUserResponse user(@PathVariable UUID userId, HttpServletRequest http) {
        requireVisitorPass(http);
        return PublicUserResponse.from(requirePublicUser(userId));
    }

    @Operation(summary = "Kullanıcının bölgeleri (yalnız açık hesap)")
    @GetMapping("/users/{userId}/territories")
    public List<TerritoryResponse> userTerritories(@PathVariable UUID userId, HttpServletRequest http) {
        requireVisitorPass(http);
        requirePublicUser(userId);
        return territoryService.userTerritories(userId);
    }

    // ---------------------------------------------------------------- kapı

    public record VerifyRequest(String turnstileToken) {
    }

    public record VisitorPassResponse(String pass, long expiresInSeconds) {
    }

    public record GateResponse(boolean required) {
    }

    /**
     * Turnstile açıkken vitrin verisi yalnız doğrulanmış ziyaretçiye açıktır.
     * Geçersiz/eksik geçiş → 403 {@code VISITOR_PASS_REQUIRED}; istemci kapıyı yeniden gösterir.
     */
    private void requireVisitorPass(HttpServletRequest http) {
        if (!turnstileService.isEnabled()) {
            return;
        }
        /*
         * 🔴 8 Ağu 2026 — OTURUM AÇMIŞ KULLANICI KAPIDAN MUAFTIR.
         *
         * Kapının amacı **botu elemektir**; giriş yapmış bir kullanıcı zaten
         * Turnstile'lı kayıt ve giriş akışından geçmiştir. Aynı kişiden ikinci
         * kez robot olmadığını kanıtlamasını istemek koruma değil, sürtünmedir.
         *
         * ⚠️ Bu muafiyet ZORUNLU hâle geldi: uygulama içindeki tüm profil
         * bağlantıları artık vitrin profiline (`/{tag}`) gidiyor. Muafiyet
         * olmasaydı kullanıcı bildirimden, aramadan ya da takipçi listesinden
         * bir profile her dokunduğunda üretimde Cloudflare doğrulaması
         * görürdü.
         *
         * ⚠️ Güvenlik gevşemez: `/public/**` uçları **yalnız okur** ve gizli
         * hesap / gizlenmiş bölge elemeleri (`requirePublicUser`,
         * `requirePublicOwner`) aynen çalışmaya devam eder. Kimliksiz
         * ziyaretçi için hiçbir şey değişmedi.
         *
         * ⚠️ `isAuthenticated()` TEK BAŞINA YETMEZ: Spring kimliksiz istekleri
         * de `AnonymousAuthenticationToken` ile taşır ve o da "authenticated"
         * sayılır → kapı herkese açılırdı. Tür kontrolü şart.
         */
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return;
        }
        if (!visitorPassService.isValid(http.getHeader(PASS_HEADER))) {
            // Ayrı kod: istemci bunu "gizli hesap" 403'ünden ayırıp kapıyı yeniden açar.
            throw new ApiException(ErrorCode.VISITOR_PASS_REQUIRED, "Ziyaretçi doğrulaması gerekli");
        }
    }

    private User requirePublicUser(UUID userId) {
        User user = userRepository.findById(userId)
                /* 🔴 16 Ağu 2026 — yönetim hesabı kimliğiyle de açılamaz.
                   Tag kapısını kapatıp bunu açık bırakmak, `/u/<adminId>`
                   köprüsünü arka kapı hâline getirirdi. */
                .filter(User::hasPublicProfile)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        if (user.isPrivateAccount()) {
            throw ApiException.forbidden("Bu hesap gizli — görmek için giriş yapın");
        }
        return user;
    }

    private void requirePublicOwner(UUID territoryId) {
        Territory territory = profileService.requireTerritory(territoryId);
        if (territory.isHidden() || territory.getStatus() != com.waydee.territory.domain.TerritoryStatus.ACTIVE) {
            throw ApiException.notFound("Alan bulunamadı");
        }
        if (territory.getOwner().isPrivateAccount()) {
            throw ApiException.forbidden("Bu hesap gizli — görmek için giriş yapın");
        }
    }
}
