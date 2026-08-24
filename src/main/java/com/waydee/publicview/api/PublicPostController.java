package com.waydee.publicview.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.TurnstileService;
import com.waydee.common.security.VisitorPassService;
import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.domain.User;
import com.waydee.social.application.PostTagService;
import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostSocialLink;
import com.waydee.social.domain.PostTag;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.PostSocialLinkRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * <b>Herkese açık gönderi</b> — bir gönderinin kimliksiz görülebilen hâli.
 *
 * <p>🔴 <b>Neden var:</b> gönderi detayı bugüne kadar YALNIZ sahibine açıktı
 * ({@code /profile-posts/{id}} sahiplik filtresiyle 404 veriyordu). Yani
 * paylaşılan bir gönderi bağlantısına tıklayan hiç kimse — takipçi bile —
 * gönderiyi göremiyordu; vitrin profilindeki ızgarada karolar da <b>tıklanamaz
 * birer resimdi</b>. Ürün, fotoğrafını alışverişe açmak üzerine kurulu olduğu
 * için bu, ürünün ana akışının kopuk olması demekti.
 *
 * <p>Yanıt bilinçli olarak <b>sahip yanıtından zengin</b>: yazar başlığı
 * (avatar + ad + vitrin adresi), ürün etiketleri ve <b>gönderinin sosyal
 * bağlantıları</b> birlikte döner — ziyaretçinin ikinci bir istek atmasına
 * gerek kalmaz ve arama motoru tek belgede tüm bağlamı görür.
 *
 * <p>🔒 Kurallar {@link PublicProfileController} ile aynı: yalnız GET, ziyaretçi
 * geçişi zorunlu, gizli/askıya alınmış hesabın gönderisi <b>hiç dönmez</b>
 * (404 — varlığı bile sızmaz), arşivlenmiş gönderi de yok sayılır.
 */
@Tag(name = "Public post", description = "Kimliksiz görülebilen gönderi detayı")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicPostController {

    private final PostRepository postRepository;
    private final PostTagService tagService;
    private final PostSocialLinkRepository socialRepository;
    private final VisitorPassService visitorPassService;
    private final TurnstileService turnstileService;
    private final com.waydee.social.infrastructure.PostLikeRepository likeRepository;
    private final com.waydee.social.infrastructure.PostSaveRepository saveRepository;
    private final com.waydee.identity.application.FollowService followService;

    /**
     * @param user oturum <b>varsa</b> doldurulur (uç {@code permitAll}, ama JWT
     *             süzgeci yine çalışır). Dolu olduğunda yanıt
     *             {@code likedByMe}/{@code savedByMe} taşır — giriş yapmış biri
     *             başkasının gönderisini açtığında kalbin dolu mu boş mu
     *             olduğunu görmeli.
     */
    @Operation(summary = "Gönderi detayı (yazar + ürün etiketleri + sosyal bağlantılar)")
    @GetMapping("/posts/{postId}")
    public PublicPostResponse post(@PathVariable UUID postId, HttpServletRequest http,
                                   @org.springframework.security.core.annotation.AuthenticationPrincipal
                                   com.waydee.common.security.AuthenticatedUser user) {
        // ⚠️ Oturum varsa ziyaretçi kapısı aranmaz: geçiş ZİYARETÇİ içindir ve
        // giriş yapmış kullanıcının elinde hiç olmaz (kapıdan geçmemiştir).
        if (user == null) {
            requirePass(http);
        }
        Post p = visible(postId, user);
        boolean liked = user != null && !likeRepository.findLikedPostIds(user.id(), List.of(postId)).isEmpty();
        boolean saved = user != null && !saveRepository.findSavedPostIds(user.id(), List.of(postId)).isEmpty();
        return PublicPostResponse.of(p, tagService.forPost(postId),
                socialRepository.findByPostIdOrderByPositionAsc(postId), liked, saved);
    }

    /**
     * <b>Paylaşım görseli</b> — sosyal kart ve arama motoru için kalıcı adres.
     *
     * <p>🔴 İmzalı medya bağlantısı <b>7 günde ölür</b>. Bir gönderi adresi
     * WhatsApp'ta ya da Google sonuçlarında aylarca yaşar; içine imzalı bağlantı
     * gömmek, kartın bir hafta sonra <b>boş resimle</b> görünmesi demekti.
     * Bu uç kalıcıdır ve her istekte <b>taze imzaya 302 yönlendirir</b>.
     *
     * <p>⚠️ Ziyaretçi geçişi İSTEMEZ: bu adresi çeken taraf tarayıcı değil,
     * Googlebot / WhatsApp / X kartı üretecidir — hiçbiri Turnstile çözemez.
     * Sızma riski yok, çünkü yalnız <b>zaten herkese açık</b> bir gönderinin
     * kapak görseline yönlendirir.
     */
    @Operation(summary = "Gönderinin paylaşım görseli (kalıcı adres)")
    @GetMapping("/posts/{postId}/image")
    public ResponseEntity<Void> image(@PathVariable UUID postId) {
        // ⚠️ Bu uç KİMLİKSİZ çağrılır (Googlebot / WhatsApp kartı): burada
        // takip ilişkisi sorulamaz, o yüzden yalnız AÇIK hesabın görseli döner.
        // Gizli hesabın kapak görseli hiçbir zaman kimliksiz servis edilmez.
        String url = postRepository.findPublicPost(postId)
                .map(PublicPostController::coverUrl)
                .orElse(null);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /**
     * Gönderiyi görünürlük kuralıyla getirir.
     *
     * <p>🔴 10 Ağu 2026 — <b>gizli hesabın TAKİPÇİSİ artık gönderiyi açabiliyor.</b>
     * Önceden burada yalnız "yazar gizli değilse" koşulu vardı ve takip ettiği
     * gizli bir hesabın karosuna tıklayan kullanıcı <b>"Gönderi bulunamadı"</b>
     * alıyordu — oysa aynı gönderi profil ızgarasında ona gösteriliyordu. Aynı
     * sorunun iki yerde iki cevabı olması hatanın kendisiydi; karar artık tek
     * yerden gelir: {@code FollowService.canViewContent}.
     *
     * <p>⚠️ 404 (403 değil): görme hakkı olmayana gönderinin <b>varlığı</b> bile
     * sızmaz.
     */
    private Post visible(UUID postId, com.waydee.common.security.AuthenticatedUser viewer) {
        Post p = postRepository.findShareablePost(postId)
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
        boolean allowed = !p.getAuthor().isPrivateAccount()
                || (viewer != null && followService.canViewContent(viewer.id(), p.getAuthor().getId()));
        if (!allowed) {
            throw ApiException.notFound("Gönderi bulunamadı");
        }
        return p;
    }

    private void requirePass(HttpServletRequest http) {
        if (!turnstileService.isEnabled()) {
            return;
        }
        if (!visitorPassService.isValid(http.getHeader(PublicViewController.PASS_HEADER))) {
            throw new ApiException(ErrorCode.VISITOR_PASS_REQUIRED, "Ziyaretçi doğrulaması gerekli");
        }
    }

    static String coverUrl(Post p) {
        return p.getMedia().isEmpty() ? null : MediaUrls.of(p.getMedia().get(0).getMedia().getId());
    }

    public record PublicPostResponse(UUID id, String title, String caption, String imageUrl,
                                     int likeCount, int saveCount, boolean likedByMe, boolean savedByMe,
                                     Instant createdAt,
                                     Author author, List<PublicTag> tags, List<PublicSocial> socialLinks) {

        static PublicPostResponse of(Post p, List<PostTag> tags, List<PostSocialLink> social,
                                     boolean liked, boolean saved) {
            User a = p.getAuthor();
            return new PublicPostResponse(
                    p.getId(), p.getTitle(), p.getCaption(), coverUrl(p),
                    p.getLikeCount(), p.getSaveCount(), liked, saved, p.getCreatedAt(),
                    new Author(a.getId(), a.getUsername(), a.getDisplayName(),
                            a.getAvatarMediaId() == null ? null : MediaUrls.of(a.getAvatarMediaId()),
                            a.hasVerifiedBadge()),
                    tags.stream().map(PublicTag::from).toList(),
                    social.stream().map(PublicSocial::from).toList());
        }
    }

    /** Gönderi başlığındaki yazar — {@code tag} vitrin adresidir ({@code /{tag}}). */
    public record Author(UUID id, String tag, String displayName, String avatarUrl, boolean verified) {
    }

    public record PublicTag(UUID id, BigDecimal x, BigDecimal y, String productUrl,
                            String productName, BigDecimal price, String currency) {
        static PublicTag from(PostTag t) {
            return new PublicTag(t.getId(), t.getX(), t.getY(), t.getProductUrl(),
                    t.getProductName(), t.getPrice(), t.getCurrency());
        }
    }

    public record PublicSocial(UUID id, String platform, String value, String url) {
        static PublicSocial from(PostSocialLink l) {
            return new PublicSocial(l.getId(), l.getPlatform(), l.getValue(),
                    com.waydee.social.api.ProfilePostController.socialUrl(l.getPlatform(), l.getValue()));
        }
    }
}
