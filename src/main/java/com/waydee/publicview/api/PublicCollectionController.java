package com.waydee.publicview.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.security.TurnstileService;
import com.waydee.common.security.VisitorPassService;
import com.waydee.common.storage.MediaUrls;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.application.FollowService;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.domain.Collection;
import com.waydee.social.infrastructure.CollectionRepository;
import com.waydee.social.infrastructure.PostRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.UUID;

/**
 * <b>Herkese açık koleksiyon</b> — bir koleksiyonun kimliksiz görülebilen hâli.
 *
 * <h3>🔴 17 Ağustos 2026 — neden yazıldı</h3>
 * <p>Koleksiyonun bir <b>"Paylaş"</b> düğmesi vardı ama paylaşılan adres iki
 * kere ölüydü: ① sunucu {@code /{username}/c/{id}} üretiyordu, arayüzde öyle
 * bir rota <b>yoktu</b> (her bağlantı 404); ② adres düzeltilse bile
 * {@code GET /collections/{id}} <b>yalnız sahibine</b> açıktı — yani
 * ziyaretçinin görebileceği bir veri kaynağı hiç yoktu.
 *
 * <p>Yani "paylaş" özelliği, paylaşılamayan bir şeyi paylaşıyordu.
 *
 * <h3>Kurallar — {@link PublicPostController} ile BİREBİR aynı</h3>
 * <ul>
 *   <li><b>Yalnız GET.</b> Hiçbir yazma ucu yok.</li>
 *   <li><b>Ziyaretçi geçişi</b> zorunlu (oturum varsa aranmaz — geçiş
 *       ziyaretçi içindir, giriş yapmış kullanıcının elinde hiç olmaz).</li>
 *   <li><b>Gizli / askıya alınmış hesabın koleksiyonu hiç dönmez</b> — 404,
 *       403 değil: varlığı bile sızmamalı. Sahibi ve <b>ACCEPTED takipçi</b>
 *       görür ({@link FollowService#canViewContent}).</li>
 *   <li><b>Engel gizlilikten ÖNCE gelir</b> — {@code canViewContent} bunu
 *       zaten bu sırayla uygular.</li>
 * </ul>
 *
 * <p>⚠️ Kuralları kopyalamak yerine {@code canViewContent} çağrılıyor: aynı
 * soruya iki ayrı yerde cevap vermek, birinin sessizce eskimesi demektir
 * (vault, 88. tur — eleme dört yüzeyde uygulanmıştı, arama ve trend
 * atlanmıştı).
 */
@Tag(name = "Public collection", description = "Kimliksiz görülebilen koleksiyon")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicCollectionController {

    /** Izgara sayfa boyu — sahibin görünümüyle aynı ritim. */
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 48;

    private final CollectionRepository collectionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FollowService followService;
    private final VisitorPassService visitorPassService;
    private final TurnstileService turnstileService;

    @Operation(summary = "Koleksiyon detayı (herkese açık)")
    @GetMapping("/collections/{id}")
    public PublicCollectionResponse collection(@PathVariable UUID id,
                                               HttpServletRequest http,
                                               @AuthenticationPrincipal AuthenticatedUser viewer) {
        if (viewer == null) {
            requirePass(http);
        }
        Collection c = visible(id, viewer);
        User owner = userRepository.findById(c.getOwnerId())
                .orElseThrow(() -> ApiException.notFound("Koleksiyon bulunamadı"));
        return PublicCollectionResponse.of(c, owner);
    }

    @Operation(summary = "Koleksiyonun gönderileri (herkese açık, sayfalı)")
    @GetMapping("/collections/{id}/posts")
    public PageResponse<com.waydee.social.api.ProfilePostController.PostTile> posts(
            @PathVariable UUID id,
            HttpServletRequest http,
            @AuthenticationPrincipal AuthenticatedUser viewer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        if (viewer == null) {
            requirePass(http);
        }
        // ⚠️ Görünürlük BURADA da sorulur. Yalnız detay ucunda sormak, gönderi
        // ızgarasını doğrudan çağıran birine kapıyı açık bırakırdı.
        visible(id, viewer);
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), s);
        return PageResponse.from(
                postRepository.findCollectionPosts(id, pageable),
                com.waydee.social.api.ProfilePostController.PostTile::from);
    }

    /**
     * Koleksiyon bu izleyiciye görünür mü.
     *
     * <p>⚠️ Bulunamayan koleksiyon ile görülemeyen koleksiyon <b>aynı yanıtı</b>
     * verir (404). Ayırmak, gizli bir hesabın koleksiyonunun var olduğunu
     * söylerdi.
     */
    private Collection visible(UUID id, AuthenticatedUser viewer) {
        Collection c = collectionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Koleksiyon bulunamadı"));
        User owner = userRepository.findById(c.getOwnerId()).orElse(null);
        if (owner == null || !owner.hasPublicProfile()) {
            // Yönetim hesabı ve pasif hesap herkese açık yüzeyde yok sayılır
            // (88. turdaki `hasPublicProfile` kuralı — beşinci yüzey burası).
            throw ApiException.notFound("Koleksiyon bulunamadı");
        }
        boolean allowed = !owner.isPrivateAccount()
                || (viewer != null && followService.canViewContent(viewer.id(), owner.getId()));
        if (!allowed) {
            throw ApiException.notFound("Koleksiyon bulunamadı");
        }
        return c;
    }

    private void requirePass(HttpServletRequest http) {
        if (!turnstileService.isEnabled()) {
            return;
        }
        if (!visitorPassService.isValid(http.getHeader(PublicViewController.PASS_HEADER))) {
            throw new ApiException(ErrorCode.VISITOR_PASS_REQUIRED, "Ziyaretçi doğrulaması gerekli");
        }
    }

    /**
     * @param ownerUsername vitrin adresi — ziyaretçi koleksiyondan sahibinin
     *                      profiline geçebilmeli; aksi halde sayfa çıkmaz sokak.
     */
    public record PublicCollectionResponse(UUID id, String title, String description,
                                           String coverUrl, int itemCount,
                                           String ownerUsername, String ownerDisplayName,
                                           String ownerAvatarUrl, boolean ownerVerified) {
        static PublicCollectionResponse of(Collection c, User owner) {
            return new PublicCollectionResponse(
                    c.getId(), c.getTitle(), c.getDescription(),
                    c.getCoverMediaId() == null ? null : MediaUrls.of(c.getCoverMediaId()),
                    c.getItemCount(),
                    owner.getUsername(), owner.getDisplayName(),
                    owner.getAvatarMediaId() == null ? null : MediaUrls.of(owner.getAvatarMediaId()),
                    owner.hasVerifiedBadge());
        }
    }
}
