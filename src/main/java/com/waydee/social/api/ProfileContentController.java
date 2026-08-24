package com.waydee.social.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.storage.MediaUrls;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.application.HtmlSanitizer;
import com.waydee.social.application.MediaService;
import com.waydee.social.domain.Collection;
import com.waydee.social.domain.CollectionPost;
import com.waydee.social.domain.Post;
import com.waydee.social.domain.ProfileLink;
import com.waydee.social.infrastructure.CollectionPostRepository;
import com.waydee.social.infrastructure.CollectionRepository;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.ProfileLinkRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/**
 * Profil içeriği: <b>koleksiyonlar</b> ve <b>bağlantılar</b>.
 *
 * <p>İkisi de kullanıcıya aittir (daireye değil) — yeni tasarımda içerik
 * profilde toplanır. Sahiplik her uçta <b>oturumdan</b> alınır; istekte
 * kullanıcı kimliği kabul edilmez.
 *
 * <p>Referans ekranında (taginbio) her sekmenin altında <b>sayfalama</b> vardır;
 * bu yüzden listeler {@link PageResponse} döner ve sayfa boyu <b>sunucuda</b>
 * sınırlanır — istemci {@code size=10000} yollayıp tüm tabloyu tek istekte
 * çekemesin.
 *
 * <p>Koleksiyon bir <b>detay ekranıdır</b>: adı yerinde düzenlenir, gönderiler
 * eklenir/çıkarılır, adresi kopyalanır ve paylaşılır.
 */
@Tag(name = "Profile content", description = "Koleksiyonlar ve profil bağlantıları")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProfileContentController {

    /** Izgara 4 sütun × 3 satır — referansın sayfa başına gösterdiği kadar. */
    private static final int DEFAULT_SIZE = 12;
    /** ⚠️ Tavan sunucuda: istemci ne isterse istesin tüm tablo tek istekte inmez. */
    private static final int MAX_SIZE = 48;

    private final CollectionRepository collectionRepository;
    private final CollectionPostRepository collectionPostRepository;
    private final ProfileLinkRepository linkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final HtmlSanitizer htmlSanitizer;

    // ------------------------------------------------------------ koleksiyon
    @Operation(summary = "Koleksiyonlarım (sayfalı)")
    @GetMapping("/collections")
    public PageResponse<CollectionResponse> collections(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        return PageResponse.from(
                collectionRepository.findByOwnerIdOrderByPositionAsc(user.id(), pageable(page, size)),
                CollectionResponse::from);
    }

    @Operation(summary = "Koleksiyon oluştur")
    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CollectionResponse createCollection(@Valid @RequestBody CollectionRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        Collection c = new Collection(user.id(), request.title().trim());
        c.setDescription(blankToNull(request.description()));
        if (request.coverMediaId() != null) {
            // Başkasının medyası kapak yapılamaz (avatar IDOR'unun aynısı).
            mediaService.assertOwnedBy(request.coverMediaId(), user.id());
            c.setCoverMediaId(request.coverMediaId());
        }
        c.setPosition((int) collectionRepository.countByOwnerId(user.id()));
        return CollectionResponse.from(collectionRepository.save(c));
    }

    @Operation(summary = "Koleksiyon detayı")
    @GetMapping("/collections/{id}")
    public CollectionDetail collection(@PathVariable UUID id,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        Collection c = ownedCollection(id, user.id());
        /*
         * ⚠️ Paylaşım adresi GÖRELİ döner: mutlak adresi istemci kendi
         * origin'iyle kurar. Sunucuya alan adı gömmek, aynı API farklı
         * adreslerden servis edildiğinde yanlış bağlantı üretirdi.
         *
         * 🔴🔴 17 Ağu 2026 — BU ADRES ÖLÜYDÜ. Burada `/{username}/c/{id}`
         * üretiliyordu ama arayüzde <b>böyle bir rota hiç yoktu</b>: paylaş
         * düğmesinin verdiği her bağlantı <b>404</b> açıyordu. Sunucu,
         * istemcinin uygulamadığı bir adres şeması uydurmuştu ve bunu hiçbir
         * test yakalamadı çünkü adres yalnız panoya kopyalanıyordu.
         *
         * ⚠️ Ders: bir SUNUCU, istemcinin ROTA TABLOSUNU bilmez. Adres üreten
         * her uç, o adresin karşılığı olduğunu varsayar — doğrulayan bir şey
         * yoksa varsayım sessizce yanlış olur. Adres artık var olan rotayı
         * gösteriyor.
         */
        /* 🔴 Paylaşım adresi artık GENEL sayfayı gösterir: `/collection/{id}`.
         * Gönderininkiyle (`/post/{id}`) simetrik — tekil ve herkese açık.
         * `/collections/{id}` (çoğul) sahibin YÖNETİM görünümü olarak kalır.
         * ⚠️ İkisini ayırmak şart: paylaşılan adres düzenleme düğmeleri
         * göstermemeli, yönetim adresi de arama motoruna girmemeli. */
        return CollectionDetail.from(c, "/collection/" + c.getId());
    }

    @Operation(summary = "Koleksiyonu yeniden adlandır")
    @PatchMapping("/collections/{id}")
    @Transactional
    public CollectionResponse renameCollection(@PathVariable UUID id,
                                               @Valid @RequestBody CollectionRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        Collection c = ownedCollection(id, user.id());
        c.setTitle(request.title().trim());
        if (request.description() != null) {
            c.setDescription(blankToNull(request.description()));
        }
        return CollectionResponse.from(collectionRepository.save(c));
    }

    @Operation(summary = "Koleksiyonu sil")
    @DeleteMapping("/collections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteCollection(@PathVariable UUID id,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        collectionRepository.delete(ownedCollection(id, user.id()));
    }

    // ------------------------------------------- koleksiyondaki gönderiler
    @Operation(summary = "Koleksiyondaki gönderiler (sayfalı)")
    @GetMapping("/collections/{id}/posts")
    public PageResponse<ProfilePostController.PostTile> collectionPosts(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size) {
        ownedCollection(id, user.id());
        return PageResponse.from(
                postRepository.findCollectionPosts(id, pageable(page, size)),
                ProfilePostController.PostTile::from);
    }

    @Operation(summary = "Koleksiyona gönderi ekle")
    @PostMapping("/collections/{id}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void addPost(@PathVariable UUID id,
                        @Valid @RequestBody AddPostRequest request,
                        @AuthenticationPrincipal AuthenticatedUser user) {
        ownedCollection(id, user.id());
        Post post = postRepository.findById(request.postId())
                .filter(p -> p.getDeletedAt() == null)
                // ⚠️ Yalnız KENDİ gönderin: başkasının gönderisi koleksiyona
                // eklenebilseydi, gizli hesabın içeriği başkasının profilinde
                // yayınlanabilirdi.
                .filter(p -> p.getAuthor().getId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));

        // Idempotent: aynı gönderi iki kez eklenirse sayaç şişmemeli.
        if (collectionPostRepository.existsByIdCollectionIdAndIdPostId(id, post.getId())) {
            return;
        }
        int position = (int) collectionPostRepository.countByIdCollectionId(id);
        collectionPostRepository.save(new CollectionPost(id, post.getId(), position));
        collectionRepository.bumpItemCount(id, 1);
    }

    @Operation(summary = "Gönderiyi koleksiyondan çıkar")
    @DeleteMapping("/collections/{id}/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void removePost(@PathVariable UUID id, @PathVariable UUID postId,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        ownedCollection(id, user.id());
        if (!collectionPostRepository.existsByIdCollectionIdAndIdPostId(id, postId)) {
            return; // idempotent — iki kez silme hata değildir
        }
        collectionPostRepository.deleteById(new CollectionPost.CollectionPostId(id, postId));
        collectionRepository.bumpItemCount(id, -1);
    }

    // -------------------------------------------------------------- bağlantı
    @Operation(summary = "Profil bağlantılarım (sayfalı)")
    @GetMapping("/profile-links")
    public PageResponse<LinkResponse> links(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "0") int size) {
        return PageResponse.from(
                linkRepository.findByOwnerIdOrderByPositionAsc(user.id(), pageable(page, size)),
                LinkResponse::from);
    }

    @Operation(summary = "Profil bağlantısı ekle")
    @PostMapping("/profile-links")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LinkResponse createLink(@Valid @RequestBody LinkRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        // 🔒 Yalnız http/https — `javascript:` gibi şemalar profilde tıklanabilir
        // zararlı bağlantı üretirdi (sosyal bağlantılarla aynı kural).
        String url = htmlSanitizer.normalizeWebsite(request.url());
        if (url == null || url.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bağlantı adresi geçersiz");
        }
        ProfileLink link = new ProfileLink(user.id(), request.title().trim(), url);
        if (request.iconMediaId() != null) {
            mediaService.assertOwnedBy(request.iconMediaId(), user.id());
            link.setIconMediaId(request.iconMediaId());
        }
        link.setPosition((int) linkRepository.countByOwnerId(user.id()));
        return LinkResponse.from(linkRepository.save(link));
    }

    /**
     * <b>Tek bağlantı</b> — detay sayfası (17 Ağu 2026).
     *
     * <p>🔴 Kullanıcı: <i>"link paylaşımında paylaşan kişi home'da tıklayınca
     * direkt açılıyor, ne silme ne düzeltme yeri açılıyor; linkin de post gibi
     * detay sayfası olsun"</i>. Doğruydu: ana sayfadaki bağlantı karosu düz bir
     * {@code <a target="_blank">} idi, yani <b>sahibi kendi bağlantısını
     * yönetemiyordu</b> — düzenlemek ya da silmek için hiçbir yol yoktu.
     *
     * <p>⚠️ Yalnız SAHİBİNE açık: başkasının bağlantısı <b>404</b> döner
     * (403 değil — kimliğin varlığı bile sızmasın, fatura ucundaki kural).
     */
    @Operation(summary = "Bağlantı detayı (yalnız sahibi)")
    @GetMapping("/profile-links/{id}")
    public LinkResponse link(@PathVariable UUID id,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        return LinkResponse.from(requireOwnLink(id, user.id()));
    }

    /**
     * <b>Bağlantıyı güncelle.</b>
     *
     * <p>⚠️ Adres yine {@code normalizeWebsite}'tan geçer — düzenleme,
     * oluşturmadaki şema kapısını atlamak için bir arka kapı olmamalı.
     * <p>⚠️ {@code iconMediaId} açıkça {@code null} gelirse simge KALDIRILIR;
     * alan hiç gönderilmezse dokunulmaz. İkisini ayırmadan "boş = sil" demek,
     * yalnız başlığı değiştiren bir isteğin simgeyi sessizce silmesi olurdu.
     */
    @Operation(summary = "Bağlantıyı güncelle")
    @PatchMapping("/profile-links/{id}")
    @Transactional
    public LinkResponse updateLink(@PathVariable UUID id,
                                   @Valid @RequestBody LinkUpdateRequest request,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        ProfileLink link = requireOwnLink(id, user.id());
        if (request.title() != null && !request.title().isBlank()) {
            link.setTitle(request.title().trim());
        }
        if (request.url() != null) {
            String url = htmlSanitizer.normalizeWebsite(request.url());
            if (url == null || url.isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bağlantı adresi geçersiz");
            }
            link.setUrl(url);
        }
        if (request.clearIcon() != null && request.clearIcon()) {
            link.setIconMediaId(null);
        } else if (request.iconMediaId() != null) {
            mediaService.assertOwnedBy(request.iconMediaId(), user.id());
            link.setIconMediaId(request.iconMediaId());
        }
        if (request.active() != null) {
            link.setActive(request.active());
        }
        return LinkResponse.from(link);
    }

    private ProfileLink requireOwnLink(UUID id, UUID ownerId) {
        return linkRepository.findById(id)
                .filter(x -> x.getOwnerId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Bağlantı bulunamadı"));
    }

    @Operation(summary = "Bağlantıyı sil")
    @DeleteMapping("/profile-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteLink(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        ProfileLink link = linkRepository.findById(id)
                .filter(x -> x.getOwnerId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("Bağlantı bulunamadı"));
        linkRepository.delete(link);
    }

    // ------------------------------------------------------------ yardımcılar
    private Collection ownedCollection(UUID id, UUID ownerId) {
        return collectionRepository.findById(id)
                .filter(x -> x.getOwnerId().equals(ownerId))
                // ⚠️ 404 (403 değil): başkasının koleksiyonunun VARLIĞI bile sızmaz.
                .orElseThrow(() -> ApiException.notFound("Koleksiyon bulunamadı"));
    }

    private static Pageable pageable(int page, int size) {
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), s);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * Kartın üstünde görünen alan adı etiketi (referansta <b>TRENDYOL.COM</b>).
     *
     * <p>⚠️ <b>Sunucuda</b> üretilir: aynı metin profilde de yönetimde de birebir
     * okunsun ve istemcinin URL ayrıştırması tutarsız kalmasın (projenin
     * "metinler sunucuda biçimlenir" kuralı).
     * <p>⚠️ {@code www.} atılır — etiket markayı göstermeli, alt alan adını değil.
     * <p>⚠️ Bozuk adreste {@code null} döner ve <b>kart yine çizilir</b>; etiket
     * süstür, içeriği yutmamalıdır.
     */
    static String hostLabel(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) return null;
            if (host.startsWith("www.")) host = host.substring(4);
            return host.toUpperCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- DTO'lar
    public record CollectionRequest(@NotBlank @Size(max = 120) String title,
                                    @Size(max = 500) String description,
                                    UUID coverMediaId) {
    }

    public record AddPostRequest(@NotNull UUID postId) {
    }

    public record LinkRequest(@NotBlank @Size(max = 120) String title,
                              @NotBlank @Size(max = 500) String url,
                              UUID iconMediaId) {
    }

    public record CollectionResponse(UUID id, String title, String description,
                                     String coverUrl, int itemCount, int position) {
        // public: vitrin profili (publicview modülü) de bu eşlemeyi kullanır —
        // ikinci bir DTO yazmak iki yerin sessizce ayrışmasına yol açardı.
        public static CollectionResponse from(Collection c) {
            return new CollectionResponse(c.getId(), c.getTitle(), c.getDescription(),
                    c.getCoverMediaId() == null ? null : MediaUrls.of(c.getCoverMediaId()),
                    c.getItemCount(), c.getPosition());
        }
    }

    /** Koleksiyon detay ekranı — ad alanı + KOPYALA/PAYLAŞ için adres. */
    public record CollectionDetail(UUID id, String title, String description,
                                   String coverUrl, int itemCount, String sharePath) {
        static CollectionDetail from(Collection c, String sharePath) {
            return new CollectionDetail(c.getId(), c.getTitle(), c.getDescription(),
                    c.getCoverMediaId() == null ? null : MediaUrls.of(c.getCoverMediaId()),
                    c.getItemCount(), sharePath);
        }
    }

    /**
     * Kısmi güncelleme — <b>tüm alanlar isteğe bağlı</b>.
     * @param clearIcon {@code true} ise simge kaldırılır ({@code iconMediaId}
     *                  yok sayılır). "Boş = sil" varsayımı, yalnız başlığı
     *                  değiştiren bir isteğin simgeyi silmesine yol açardı.
     */
    public record LinkUpdateRequest(
            @jakarta.validation.constraints.Size(max = 140) String title,
            @jakarta.validation.constraints.Size(max = 500) String url,
            UUID iconMediaId,
            Boolean clearIcon,
            Boolean active) {
    }

    public record LinkResponse(UUID id, String title, String url, String iconUrl,
                               String domainLabel, int position, int clickCount, boolean active) {
        public static LinkResponse from(ProfileLink l) {
            return new LinkResponse(l.getId(), l.getTitle(), l.getUrl(),
                    l.getIconMediaId() == null ? null : MediaUrls.of(l.getIconMediaId()),
                    // ⚠️ `hostLabel` adı bilinçli: record'un `domainLabel()`
                    // erişimcisiyle aynı adı taşısaydı derleyici onu çağırmaya
                    // çalışırdı (bir kez derlemeyi kırdı).
                    hostLabel(l.getUrl()),
                    l.getPosition(), l.getClickCount(), l.isActive());
        }
    }
}
