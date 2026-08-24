package com.waydee.social.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.storage.MediaUrls;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.moderation.application.RestrictionService;
import com.waydee.moderation.domain.RestrictedAction;
import com.waydee.social.application.MediaService;
import com.waydee.social.application.PostTagService;
import com.waydee.social.domain.MediaObject;
import com.waydee.social.domain.Post;
import com.waydee.social.infrastructure.MediaObjectRepository;
import com.waydee.social.infrastructure.PostRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * <b>Profil gönderisi</b> — daire olmadan, doğrudan kullanıcıya ait gönderi.
 *
 * <p>Yeni tasarımda akış şudur: kullanıcı ana sayfadan <b>+</b> → Post der,
 * bir ad verir, fotoğrafı yükler/kırpar, sonra fotoğrafın üstüne <b>ürün
 * etiketleri</b> bırakır. Ortada hiçbir daire yoktur — bu yüzden V30 ile
 * {@code posts.territory_id} zorunlu olmaktan çıktı.
 *
 * <p>⚠️ Gönderi ve etiketleri <b>tek transaction</b>'da yazılır: yarım kalmış
 * bir gönderi (fotoğraf var, etiketler yok) kullanıcının gözünde bozuk bir
 * kayıttır.
 */
@Tag(name = "Profile posts", description = "Profil gönderileri (dairesiz)")
@RestController
@RequestMapping("/api/v1/profile-posts")
@RequiredArgsConstructor
public class ProfilePostController {

    private final PostRepository postRepository;
    private final MediaObjectRepository mediaRepository;
    private final MediaService mediaService;
    private final UserRepository userRepository;
    private final PostTagService tagService;
    private final RestrictionService restrictionService;
    private final com.waydee.identity.application.PlanService planService;
    private final com.waydee.social.application.LinkPreviewService linkPreview;
    private final com.waydee.social.infrastructure.PostSocialLinkRepository socialRepository;
    private final com.waydee.social.infrastructure.PostLikeRepository likeRepository;
    private final com.waydee.social.infrastructure.PostSaveRepository saveRepository;

    /** Izgara 4 sütun × 3 satır — referansın sayfa başına gösterdiği kadar. */
    private static final int DEFAULT_SIZE = 12;
    /** ⚠️ Tavan sunucuda: istemci tüm gönderileri tek istekte çekemesin. */
    private static final int MAX_SIZE = 48;

    @Operation(summary = "Gönderilerim (profil ızgarası, sayfalı)")
    @GetMapping
    public PageResponse<PostTile> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "0") int size) {
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageResponse.from(
                postRepository.findProfilePosts(user.id(), PageRequest.of(Math.max(page, 0), s)),
                PostTile::from);
    }

    @Operation(summary = "Profil gönderisi oluştur (fotoğraf + ürün etiketleri)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PostTile create(@Valid @RequestBody CreateProfilePostRequest request,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        // Moderasyon kapısı: kısıtlı kullanıcı gönderi açamaz (diğer akışlarla aynı).
        restrictionService.assertAllowed(user.id(), RestrictedAction.POST);
        // 🔒 Plan kapısı: ücretsiz planda haftada 1 gönderi (V33, V41'de haftalığa çekildi).
        planService.assertCanCreatePost(user.id());
        planService.assertTagCount(user.id(), request.tags() == null ? 0 : request.tags().size());

        // Fotoğraf kullanıcının kendi medyası olmalı (IDOR kapısı).
        mediaService.assertOwnedBy(request.mediaId(), user.id());
        MediaObject media = mediaRepository.findById(request.mediaId())
                .orElseThrow(() -> ApiException.notFound("Görsel bulunamadı"));
        User author = userRepository.findById(user.id())
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        Post post = new Post(author, trim(request.title()), trim(request.caption()));
        post.attachMedia(media, 0);
        Post saved = postRepository.save(post);

        // Etiketler aynı transaction'da; biri yazılıp diğeri yazılmadan kalmaz.
        if (request.tags() != null && !request.tags().isEmpty()) {
            List<PostTagService.TagInput> inputs = request.tags().stream()
                    .map(t -> new PostTagService.TagInput(t.x(), t.y(), t.productUrl(),
                            t.productName(), t.price(), t.currency(), t.imageMediaId()))
                    .toList();
            tagService.replace(saved.getId(), user.id(), inputs);
        }
        return PostTile.from(saved);
    }

    /**
     * Gönderi detayı — <b>sahibinin</b> penceresi.
     *
     * <p>🔴 Yanıt sosyal bağlantıları ve beğeni/kaydetme <b>durumunu</b> da
     * taşır. Önceden yalnız sayaçlar dönüyordu: sahibi kendi gönderisine
     * bastığında eklediği sosyal hesapları göremiyor, kalbin dolu mu boş mu
     * olduğunu bilemiyordu — ekran ölü sayaçlardan ibaretti. Ziyaretçi yanıtı
     * ({@code /public/posts/{id}}) ile alan alan aynı hizada tutuldu ki
     * arayüzde <b>tek bir detay bileşeni</b> iki tarafa da yetsin.
     */
    @Operation(summary = "Gönderi detayı (ürün etiketleri + sosyal bağlantılar)")
    @GetMapping("/{id}")
    public PostDetail detail(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        Post p = owned(id, user.id());
        return PostDetail.from(p, tagService.forPost(p.getId()),
                socialRepository.findByPostIdOrderByPositionAsc(p.getId()),
                !likeRepository.findLikedPostIds(user.id(), List.of(p.getId())).isEmpty(),
                !saveRepository.findSavedPostIds(user.id(), List.of(p.getId())).isEmpty());
    }

    @Operation(summary = "Gönderiyi düzenle (ad + açıklama)")
    @PatchMapping("/{id}")
    @Transactional
    public PostTile edit(@PathVariable UUID id, @Valid @RequestBody EditPostRequest request,
                         @AuthenticationPrincipal AuthenticatedUser user) {
        Post p = owned(id, user.id());
        p.edit(trim(request.title()), trim(request.caption()));
        return PostTile.from(postRepository.save(p));
    }

    /**
     * Arşivle / arşivden çıkar.
     *
     * <p>⚠️ Silme DEĞİLDİR: referansın ⋯ menüsünde "Archive" ve "Delete" ayrı
     * durur. Arşiv geri alınabilir bir gizlemedir (V31).
     */
    @Operation(summary = "Gönderiyi arşivle / geri al")
    @PostMapping("/{id}/archive")
    @Transactional
    public PostTile archive(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean value,
                            @AuthenticationPrincipal AuthenticatedUser user) {
        Post p = owned(id, user.id());
        p.setArchived(value);
        return PostTile.from(postRepository.save(p));
    }

    @Operation(summary = "Gönderiyi sil")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        Post p = owned(id, user.id());
        // Soft-delete: kullanıcı içerikleri projede hiçbir zaman satır silmez
        // (yorum/beğeni gibi bağlı kayıtlar ve denetim izi korunur).
        p.softDelete();
        postRepository.save(p);
    }

    /**
     * Var olan gönderiye <b>tek bir ürün etiketi</b> ekler.
     *
     * <p>⚠️ {@code PostTagService.replace} kullanılmaz: o, listenin tamamını
     * değiştirir ve düzenleme ekranında tek etiket eklerken diğerlerini
     * silerdi. Burada mevcut etiketler okunup sonuna yeni olan eklenir.
     */
    @Operation(summary = "Gönderiye ürün etiketi ekle")
    @PostMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<TagView> addTag(@PathVariable UUID id,
                                @Valid @RequestBody TagItem request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        owned(id, user.id());
        // 🔒 Mevcut + yeni etiket sayısı plan sınırını aşamaz.
        planService.assertTagCount(user.id(), tagService.forPost(id).size() + 1);
        /*
         * 🔴 17 Ağu 2026 — `replace` YERİNE `addOne`.
         *
         * Eski hâl bütün etiketleri silip yeniden yazıyordu; satırlar YENİ
         * kimlik alıyor ve {@code post_tag_daily_stats} ON DELETE CASCADE ile
         * bağlı olduğu için **eski etiketlerin istatistiği siliniyordu**.
         * Yani ikinci etiketi eklemek, birincinin ölçümünü sıfırlıyordu ve
         * bu hiçbir yerde görünmüyordu.
         */
        tagService.addOne(id, user.id(), new PostTagService.TagInput(
                request.x(), request.y(), request.productUrl(),
                request.productName(), request.price(), request.currency(), request.imageMediaId()));
        return tagService.forPost(id).stream().map(TagView::from).toList();
    }

    /**
     * Var olan bir etiketi <b>günceller</b> (17 Ağu 2026).
     *
     * <p>🔴 Kullanıcı: <i>"etiket ekleme kısmında etiketlerin üstüne gelince
     * etiketleri düzenleyebilelim"</i>. Öncesinde bir etiket kaydedildikten
     * sonra <b>değiştirilemiyordu</b>: tek çare silip yeniden eklemekti ve
     * silme ucu da yoktu — yani yanlış yazılan bir fiyat kalıcıydı.
     *
     * <p>⚠️ Plan sınırı burada <b>KONTROL EDİLMEZ</b>: güncelleme etiket
     * sayısını değiştirmez. Kontrol koymak, sınıra dayanmış bir ücretsiz
     * hesabın mevcut etiketini düzeltmesini de engellerdi.
     */
    @Operation(summary = "Ürün etiketini güncelle")
    @PatchMapping("/{id}/tags/{tagId}")
    @Transactional
    public List<TagView> updateTag(@PathVariable UUID id,
                                   @PathVariable UUID tagId,
                                   @Valid @RequestBody TagItem request,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        owned(id, user.id());
        tagService.updateOne(id, tagId, user.id(), new PostTagService.TagInput(
                request.x(), request.y(), request.productUrl(),
                request.productName(), request.price(), request.currency(), request.imageMediaId()));
        return tagService.forPost(id).stream().map(TagView::from).toList();
    }

    /** Tek bir ürün etiketini siler (17 Ağu 2026 — daha önce yolu yoktu). */
    @Operation(summary = "Ürün etiketini sil")
    @DeleteMapping("/{id}/tags/{tagId}")
    @Transactional
    public List<TagView> deleteTag(@PathVariable UUID id,
                                   @PathVariable UUID tagId,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        owned(id, user.id());
        tagService.deleteOne(id, tagId, user.id());
        return tagService.forPost(id).stream().map(TagView::from).toList();
    }

    /**
     * <b>Auto Fetch Data</b> — ürün adresinden ad/görsel/fiyat çıkarır.
     *
     * <p>⚠️ Kimlik ister (kimliksiz olsaydı sunucumuz herkesin kullanabileceği
     * bir "adres tarayıcısı" olurdu) ve hız sınırı zaten API kovasında.
     * <p>⚠️ Başarısızlık hata değildir: alanlar boş döner, kullanıcı elle doldurur.
     */
    @Operation(summary = "Bağlantı önizlemesi (Auto Fetch Data)")
    @GetMapping("/link-preview")
    public com.waydee.social.application.LinkPreviewService.Preview linkPreview(
            @RequestParam String url,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return linkPreview.fetch(url);
    }

    @Operation(summary = "Gönderinin sosyal bağlantıları")
    @GetMapping("/{id}/social-links")
    public List<SocialView> socialLinks(@PathVariable UUID id,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        owned(id, user.id());
        return socialRepository.findByPostIdOrderByPositionAsc(id).stream().map(SocialView::from).toList();
    }

    /**
     * Gönderiye sosyal hesap ekler (aynı platform ikinci kez gelirse GÜNCELLER).
     *
     * <p>⚠️ Adres çözümü {@code SocialLinkService.resolveUrl} ile yapılır —
     * kullanıcı adı yazıldıysa platform öneki eklenir ve <b>yalnız http/https</b>
     * kabul edilir (profil bağlantılarıyla aynı güvenlik kuralı).
     */
    @Operation(summary = "Gönderiye sosyal bağlantı ekle")
    @PostMapping("/{id}/social-links")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SocialView addSocial(@PathVariable UUID id,
                                @Valid @RequestBody SocialRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        owned(id, user.id());
        String platform = request.platform().trim().toUpperCase(java.util.Locale.ROOT);
        String value = request.value().trim();

        /*
         * 🔒 Adres üretilemiyorsa kayıt HİÇ AÇILMAZ.
         *
         * Önce yalnız bağlantıyı üretmiyorduk ama satırı yazıyorduk; sonuç:
         * arayüzde `href` boş, tıklanamayan ÖLÜ bir ikon çiziliyordu (ölçüldü:
         * `javascript://evil` → satır yazıldı, `url = null`). Güvenlik zaten
         * sağlamdı, ama kullanıcıya bozuk bir kayıt bırakmak da bir hatadır —
         * doğrusu girdiyi baştan reddetmek.
         */
        if (socialUrl(platform, value) == null) {
            throw new ApiException(com.waydee.common.error.ErrorCode.VALIDATION_ERROR,
                    "Bağlantı adresi geçersiz");
        }

        var existing = socialRepository.findByPostIdAndPlatform(id, platform);
        var link = existing.orElseGet(() -> new com.waydee.social.domain.PostSocialLink(
                id, platform, value, (int) socialRepository.countByPostId(id)));
        link.setValue(value);
        return SocialView.from(socialRepository.save(link));
    }

    @Operation(summary = "Gönderinin sosyal bağlantısını kaldır")
    @DeleteMapping("/{id}/social-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removeSocial(@PathVariable UUID id, @PathVariable UUID linkId,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        owned(id, user.id());
        socialRepository.findById(linkId)
                .filter(l -> l.getPostId().equals(id))
                .ifPresent(socialRepository::delete);
    }

    /**
     * ⚠️ 404 (403 değil): başkasının gönderisinin VARLIĞI bile sızmaz.
     *
     * <p>⚠️ {@code findWithDetailsById} kullanılır, düz {@code findById} DEĞİL:
     * detay yanıtı {@code media}'ya dokunuyor ve transaction dışında lazy
     * koleksiyon patlıyordu (ölçüldü: 500).
     */
    private Post owned(UUID id, UUID userId) {
        return postRepository.findWithDetailsById(id)
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> p.getAuthor().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
    }

    private static String trim(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    public record CreateProfilePostRequest(
            @Size(max = 140) String title,
            @Size(max = 1000) String caption,
            @NotNull UUID mediaId,
            @Size(max = 20) List<TagItem> tags
    ) {
    }

    public record TagItem(BigDecimal x, BigDecimal y, String productUrl, String productName,
                          BigDecimal price, String currency, UUID imageMediaId) {
    }

    public record SocialRequest(@NotBlank @Size(max = 20) String platform,
                                @NotBlank @Size(max = 300) String value) {
    }

    public record SocialView(UUID id, String platform, String value, String url) {
        static SocialView from(com.waydee.social.domain.PostSocialLink l) {
            return new SocialView(l.getId(), l.getPlatform(), l.getValue(), socialUrl(l.getPlatform(), l.getValue()));
        }
    }

    /**
     * Ham değeri tıklanabilir adrese çevirir (identity modülündeki
     * {@code SocialLinkService.resolveUrl} ile aynı kural).
     *
     * <p>⚠️ Kural kopyalandı, servis <b>çağrılmadı</b>: o servis {@code identity}
     * modülünde ve modüller birbirinin iç servisini çağırmaz (mimari kuralı).
     * <p>🔒 Şemasız değer kullanıcı adı sayılır ve platform önekiyle birleşir;
     * şema yazılmışsa <b>yalnız http/https</b> kabul edilir — {@code javascript:}
     * gibi bir şema profilde tıklanabilir zararlı bağlantı üretirdi.
     */
    public static String socialUrl(String platform, String value) {
        String v = value.trim();
        String lower = v.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return v;
        }
        if (lower.contains("://")) {
            return null; // http/https dışında şema — bağlantı üretilmez
        }
        if ("MAIL".equals(platform)) {
            return v.contains("@") ? "mailto:" + v : null;
        }
        String handle = v.replaceFirst("^[@/]+", "");
        return switch (platform) {
            case "INSTAGRAM" -> "https://instagram.com/" + handle;
            case "TIKTOK" -> "https://tiktok.com/@" + handle;
            case "SNAPCHAT" -> "https://snapchat.com/add/" + handle;
            case "X" -> "https://x.com/" + handle;
            case "THREADS" -> "https://threads.net/@" + handle;
            // LINK ve WEBSITE'ta kullanıcı adı kavramı yok: şemasız yazılmışsa
            // https:// eklenir (normalizeWebsite deseni).
            default -> "https://" + handle;
        };
    }

    public record EditPostRequest(@Size(max = 140) String title,
                                  @Size(max = 1000) String caption) {
    }

    /**
     * Gönderi detay penceresi — görsel + etiketler + sosyal bağlantılar + sayaçlar.
     *
     * <p>⚠️ {@code authorTag} vitrin adresidir ({@code /{tag}}); pencere
     * başlığındaki yazar satırı buradan çizilir.
     */
    public record PostDetail(UUID id, String title, String caption, String imageUrl,
                             int likeCount, int saveCount, boolean archived,
                             boolean likedByMe, boolean savedByMe,
                             String authorTag, String authorName, String authorAvatarUrl,
                             List<TagView> tags, List<SocialView> socialLinks) {
        static PostDetail from(Post p, List<com.waydee.social.domain.PostTag> tags,
                               List<com.waydee.social.domain.PostSocialLink> social,
                               boolean liked, boolean saved) {
            String url = p.getMedia().isEmpty() ? null
                    : MediaUrls.of(p.getMedia().get(0).getMedia().getId());
            var author = p.getAuthor();
            return new PostDetail(p.getId(), p.getTitle(), p.getCaption(), url,
                    p.getLikeCount(), p.getSaveCount(), p.isArchived(), liked, saved,
                    author.getUsername(), author.getDisplayName(),
                    author.getAvatarMediaId() == null ? null : MediaUrls.of(author.getAvatarMediaId()),
                    tags.stream().map(TagView::from).toList(),
                    social.stream().map(SocialView::from).toList());
        }
    }

    /**
     * @param imageMediaId 🔴 17 Ağu 2026'da eklendi. {@code imageUrl} <b>imzalı
     *                     ve süreli</b> bir adrestir; ondan medya kimliğine geri
     *                     dönülemez. Düzenleme formu etiketi kaydederken bu
     *                     kimliği geri göndermek zorunda — yoksa PATCH'te
     *                     {@code null} gider ve <b>ürün görseli sessizce
     *                     silinirdi</b>.
     */
    public record TagView(UUID id, BigDecimal x, BigDecimal y, String productUrl,
                          String productName, BigDecimal price, String currency,
                          String imageUrl, UUID imageMediaId) {
        static TagView from(com.waydee.social.domain.PostTag t) {
            return new TagView(t.getId(), t.getX(), t.getY(), t.getProductUrl(),
                    t.getProductName(), t.getPrice(), t.getCurrency(),
                    t.getImageMediaId() == null ? null : MediaUrls.of(t.getImageMediaId()),
                    t.getImageMediaId());
        }
    }

    public record PostTile(UUID id, String title, String caption, String imageUrl,
                           int tagCount, int likeCount, Instant createdAt) {
        public static PostTile from(Post p) {
            String url = p.getMedia().isEmpty() ? null
                    : MediaUrls.of(p.getMedia().get(0).getMedia().getId());
            return new PostTile(p.getId(), p.getTitle(), p.getCaption(), url,
                    p.getTagCount(), p.getLikeCount(), p.getCreatedAt());
        }
    }
}
