package com.waydee.marketplace.application;

import com.waydee.common.error.ApiException;
import com.waydee.marketplace.api.dto.StoreStudioDtos.CandidatePost;
import com.waydee.marketplace.api.dto.StoreStudioDtos.ReorderRequest;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StoreProductRequest;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StoreProductView;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StudioSettingsRequest;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StudioSettingsView;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StudioView;
import com.waydee.marketplace.domain.ListingStatus;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceListing;
import com.waydee.marketplace.domain.StoreProduct;
import com.waydee.marketplace.domain.StoreProductSource;
import com.waydee.marketplace.domain.StoreSettings;
import com.waydee.marketplace.infrastructure.MarketplaceListingRepository;
import com.waydee.marketplace.infrastructure.MarketplaceRepository;
import com.waydee.marketplace.infrastructure.StoreProductRepository;
import com.waydee.marketplace.infrastructure.StoreSettingsRepository;
import com.waydee.social.api.dto.SocialDtos.PostResponse;
import com.waydee.social.application.HtmlSanitizer;
import com.waydee.social.application.MediaService;
import com.waydee.social.application.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Mağaza stüdyosu</b> — kabul edilen stant sahibinin 3B dükkânını yönettiği
 * servis: raf ürünleri, televizyon videosu, müzik, ışık.
 *
 * <h2>Kapı</h2>
 * <p>Her işlem {@link #requireOwned} ile başlar: stant <b>bu kullanıcıya ait</b>
 * ve <b>APPROVED</b> olmalı. Onay beklemedeki bir başvurunun sahibi dükkânını
 * döşeyemez — henüz bir dükkânı yoktur.
 *
 * <h2>⚠️ Raf kapasitesi</h2>
 * <p>{@link #SHELF_CAPACITY} sunucuda zorlanır. İstemcide "ekle" düğmesini
 * gizlemek yeterli değildir; sınır aşılırsa sahnedeki doku sayısı patlar ve
 * orta seviye telefonlar düşer (vault: mağaza başına 12 doku kararı).
 */
@Service
@RequiredArgsConstructor
public class StoreStudioService {

    /**
     * Rafa konabilecek azami ürün.
     *
     * <p>⚠️ {@code MarketplaceWorldService.SHELF_CAPACITY} ile <b>aynı sayı</b>
     * olmak zorunda: biri 12 diğeri 20 olsaydı stüdyoda eklenen ürün sahnede
     * hiç görünmez, kullanıcı da sebebini anlayamazdı.
     */
    public static final int SHELF_CAPACITY = 12;

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceRepository marketplaceRepository;
    private final StoreSettingsRepository settingsRepository;
    private final StoreProductRepository productRepository;
    private final PostService postService;
    private final MediaService mediaService;
    private final HtmlSanitizer htmlSanitizer;

    // ------------------------------------------------------------------ okuma

    @Transactional
    public StudioView studio(UUID listingId, UUID userId) {
        MarketplaceListing listing = requireOwned(listingId, userId);
        Marketplace market = marketplaceRepository.findById(listing.getMarketplaceId()).orElse(null);
        StoreSettings settings = settingsOf(listingId);

        List<StoreProduct> products = productRepository.findByListingIdOrderByPositionAsc(listingId);
        Map<UUID, String> postImages = postImagesFor(products, userId);

        return new StudioView(
                listingId,
                market != null ? market.getSlug() : null,
                market != null ? market.getName() : null,
                listing.getTitle(),
                listing.getStatus().name(),
                StudioSettingsView.from(settings, listing.getLogoMediaId()),
                products.stream().map(p -> StoreProductView.from(p, imageFor(p, postImages))).toList(),
                candidates(listingId, userId),
                SHELF_CAPACITY);
    }

    /**
     * Rafa eklenebilecek gönderiler — <b>zaten eklenmiş olanlar düşülür</b>.
     *
     * <p>⚠️ Eklenmişleri listede bırakmak, kullanıcının aynı ürünü ikinci kez
     * eklemeye çalışıp veritabanı kısıtından ({@code uq_store_products_post})
     * hata yemesi demekti. Seçenek olarak sunulmayan şey, hata da üretmez.
     */
    private List<CandidatePost> candidates(UUID listingId, UUID userId) {
        List<StoreProduct> existing = productRepository.findByListingIdOrderByPositionAsc(listingId);
        var used = existing.stream().map(StoreProduct::getPostId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return postService.byAuthor(userId, userId, 0, 48).content().stream()
                .filter(p -> p.mediaUrls() != null && !p.mediaUrls().isEmpty())
                .filter(p -> !used.contains(p.id()))
                .map(p -> new CandidatePost(p.id(), p.mediaUrls().getFirst(), p.caption()))
                .toList();
    }

    /** POST kaynaklı ürünlerin görselleri — gönderiden okunur, kopyalanmaz. */
    private Map<UUID, String> postImagesFor(List<StoreProduct> products, UUID viewerId) {
        boolean anyPost = products.stream().anyMatch(p -> p.getPostId() != null);
        if (!anyPost) {
            return Map.of();
        }
        Map<UUID, String> images = new HashMap<>();
        for (PostResponse post : postService.byAuthor(viewerId, viewerId, 0, 100).content()) {
            if (post.mediaUrls() != null && !post.mediaUrls().isEmpty()) {
                images.put(post.id(), post.mediaUrls().getFirst());
            }
        }
        return images;
    }

    // ------------------------------------------------------------------ ayarlar

    @Transactional
    public StudioSettingsView updateSettings(UUID listingId, UUID userId, StudioSettingsRequest r) {
        MarketplaceListing listing = requireOwned(listingId, userId);
        StoreSettings s = settingsOf(listingId);

        /*
         * 🔴 LOGO stantın kendisinde durur (`marketplace_listings.logo_media_id`)
         * — 3B tabela ve liste kartı orayı okuyor. Stüdyodan değiştirmek
         * başvuruyu YENİDEN İNCELEMEYE DÜŞÜRMEZ: logo onay gerektiren bir
         * başvuru verisi değil, sahibinin sunum tercihidir.
         */
        if (r.logoMediaId() != null) {
            mediaService.assertOwnedBy(r.logoMediaId(), userId);
            listing.setLogoMediaId(r.logoMediaId());
            listingRepository.save(listing);
        }

        if (r.displayName() != null) {
            s.setDisplayName(r.displayName().isBlank() ? null : r.displayName().trim());
        }

        /* 🔒 Başkasının medyası kendi mağazasına basılamaz — vitrin logosunda
           yaşanan IDOR'un aynısı. Sahiplik HER medya alanında kontrol edilir. */
        if (r.videoMediaId() != null) {
            mediaService.assertOwnedBy(r.videoMediaId(), userId);
            s.setVideoMediaId(r.videoMediaId());
        }
        if (r.musicMediaId() != null) {
            mediaService.assertOwnedBy(r.musicMediaId(), userId);
            s.setMusicMediaId(r.musicMediaId());
        }

        if (r.tvEnabled() != null) {
            s.setTvEnabled(r.tvEnabled());
        }
        if (r.tvMuted() != null) {
            s.setTvMuted(r.tvMuted());
        }
        if (r.musicEnabled() != null) {
            s.setMusicEnabled(r.musicEnabled());
        }
        if (r.musicVolume() != null) {
            s.setMusicVolume(r.musicVolume());
        }
        if (r.lightColor() != null) {
            s.setLightColor(r.lightColor());
        }
        if (r.lightIntensity() != null) {
            s.setLightIntensity(r.lightIntensity());
        }
        if (r.accentColor() != null) {
            s.setAccentColor(r.accentColor());
        }
        if (r.facadeColor() != null) {
            s.setFacadeColor(r.facadeColor());
        }
        return StudioSettingsView.from(settingsRepository.save(s), listing.getLogoMediaId());
    }

    /** Televizyon videosunu kaldır (dosya depoda kalır, bağ kopar). */
    @Transactional
    public StudioSettingsView clearVideo(UUID listingId, UUID userId) {
        MarketplaceListing listing = requireOwned(listingId, userId);
        StoreSettings s = settingsOf(listingId);
        s.setVideoMediaId(null);
        return StudioSettingsView.from(settingsRepository.save(s), listing.getLogoMediaId());
    }

    @Transactional
    public StudioSettingsView clearMusic(UUID listingId, UUID userId) {
        MarketplaceListing listing = requireOwned(listingId, userId);
        StoreSettings s = settingsOf(listingId);
        s.setMusicMediaId(null);
        s.setMusicEnabled(false);
        return StudioSettingsView.from(settingsRepository.save(s), listing.getLogoMediaId());
    }

    // ------------------------------------------------------------------ ürünler

    @Transactional
    public StoreProductView addProduct(UUID listingId, UUID userId, StoreProductRequest r) {
        requireOwned(listingId, userId);

        if (productRepository.countByListingId(listingId) >= SHELF_CAPACITY) {
            throw ApiException.badRequest(
                    "Rafta en fazla " + SHELF_CAPACITY + " ürün olabilir. Önce birini kaldır.");
        }

        StoreProductSource source = StoreProductSource.valueOf(r.source());
        int position = productRepository.nextPosition(listingId);

        StoreProduct product;
        if (source == StoreProductSource.POST) {
            if (r.postId() == null) {
                throw ApiException.badRequest("Profilden ürün eklemek için gönderi seçilmeli");
            }
            /* 🔒 Gönderi gerçekten BU kullanıcının mı? Aksi hâlde başkasının
               fotoğrafı kendi rafına konabilirdi. `byAuthor` süzgeci bunu
               garanti etmez — istek doğrudan bir kimlikle geliyor. */
            postService.assertAuthoredBy(r.postId(), userId);
            if (productRepository.existsByListingIdAndPostId(listingId, r.postId())) {
                throw ApiException.badRequest("Bu gönderi zaten rafta");
            }
            product = StoreProduct.fromPost(listingId, r.postId(), r.title().trim(), position);
        } else {
            product = StoreProduct.custom(listingId, r.title().trim(), position);
            if (r.imageMediaId() != null) {
                mediaService.assertOwnedBy(r.imageMediaId(), userId);
                product.setImageMediaId(r.imageMediaId());
            }
        }
        applyCommon(product, r);
        StoreProduct saved = productRepository.save(product);
        return StoreProductView.from(saved, imageOfPost(saved, userId));
    }

    @Transactional
    public StoreProductView updateProduct(UUID listingId, UUID productId, UUID userId, StoreProductRequest r) {
        requireOwned(listingId, userId);
        StoreProduct product = requireProduct(listingId, productId);

        product.setTitle(r.title().trim());
        if (product.getSource() == StoreProductSource.CUSTOM && r.imageMediaId() != null) {
            mediaService.assertOwnedBy(r.imageMediaId(), userId);
            product.setImageMediaId(r.imageMediaId());
        }
        applyCommon(product, r);
        return StoreProductView.from(productRepository.save(product), imageOfPost(product, userId));
    }

    @Transactional
    public void removeProduct(UUID listingId, UUID productId, UUID userId) {
        requireOwned(listingId, userId);
        productRepository.delete(requireProduct(listingId, productId));
    }

    /**
     * Raf sırasını toptan yaz.
     *
     * <p>⚠️ Gelen listedeki <b>her kimlik bu stanta ait olmalı</b>; aksi hâlde
     * kullanıcı başka bir mağazanın ürününü kendi isteğiyle yeniden
     * sıralayabilirdi. Eksik kalan ürünler sıranın sonuna düşer.
     */
    @Transactional
    public List<StoreProductView> reorder(UUID listingId, UUID userId, ReorderRequest r) {
        requireOwned(listingId, userId);
        List<StoreProduct> all = productRepository.findByListingIdOrderByPositionAsc(listingId);
        Map<UUID, StoreProduct> byId = new LinkedHashMap<>();
        all.forEach(p -> byId.put(p.getId(), p));

        int position = 0;
        for (UUID id : r.productIds()) {
            StoreProduct p = byId.remove(id);
            if (p == null) {
                throw ApiException.badRequest("Sıralamada bu mağazaya ait olmayan bir ürün var");
            }
            p.setPosition(position++);
        }
        // Listeye konmamış olanlar sona — sessizce kaybolmasınlar.
        for (StoreProduct leftover : byId.values()) {
            leftover.setPosition(position++);
        }
        productRepository.saveAll(all);

        Map<UUID, String> images = postImagesFor(all, userId);
        return all.stream()
                .sorted(java.util.Comparator.comparingInt(StoreProduct::getPosition))
                .map(p -> StoreProductView.from(p, imageFor(p, images)))
                .toList();
    }

    // ------------------------------------------------------------------ yardımcılar

    /**
     * Ürünün gönderi görseli — <b>yalnız POST kaynaklıysa</b> haritada aranır.
     *
     * <p>🔴 Bu yardımcı bir NPE yüzünden var. Önce doğrudan
     * {@code postImages.get(p.getPostId())} yazılmıştı; CUSTOM üründe
     * {@code postId} <b>null</b> ve harita boşken {@link Map#of()} döndüğü için
     * çağrı patlıyordu: {@code Map.of()} değişmez bir maptir ve
     * <b>null anahtarda {@code get} bile NPE atar</b> — {@code HashMap} atmaz.
     *
     * <p>⚠️ Ölçüldü (15 Ağu): stüdyoya CUSTOM ürün eklendikten sonra
     * {@code GET …/studio} → <b>500</b>. Yani hata boş rafta değil, ürün
     * eklenince ortaya çıkıyordu — "çalışıyor" sanılması bundan.
     */
    private static String imageFor(StoreProduct p, Map<UUID, String> postImages) {
        return p.getPostId() == null ? null : postImages.get(p.getPostId());
    }

    private void applyCommon(StoreProduct p, StoreProductRequest r) {
        p.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        p.setPrice(r.price());
        p.setCurrency(r.currency());
        // 🔒 Yalnız http/https — rafta tıklanabilir zararlı bağlantı oluşamaz.
        p.setProductUrl(r.productUrl() == null || r.productUrl().isBlank()
                ? null
                : htmlSanitizer.normalizeWebsite(r.productUrl(), 500));
        if (r.visible() != null) {
            p.setVisible(r.visible());
        }
    }

    private String imageOfPost(StoreProduct p, UUID viewerId) {
        return p.getPostId() == null ? null : postImagesFor(List.of(p), viewerId).get(p.getPostId());
    }

    private StoreProduct requireProduct(UUID listingId, UUID productId) {
        StoreProduct p = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Ürün bulunamadı"));
        /* ⚠️ Ürünün BU stanta ait olduğu ayrıca kontrol edilir: kimlik tahmin
           edilebilir olmasa da, sahiplik kontrolü kimliğin gizliliğine
           dayandırılmaz. */
        if (!p.getListingId().equals(listingId)) {
            throw ApiException.notFound("Ürün bulunamadı");
        }
        return p;
    }

    /** Ayar satırı ilk erişimde yaratılır — kullanıcı "kur" adımı görmemeli. */
    private StoreSettings settingsOf(UUID listingId) {
        return settingsRepository.findById(listingId)
                .orElseGet(() -> settingsRepository.save(new StoreSettings(listingId)));
    }

    /**
     * Stant bu kullanıcıya ait ve <b>onaylı</b> mı?
     *
     * <p>⚠️ Onay şartı bilinçli: stüdyo, "kabul edilen kişinin" panelidir.
     * PENDING bir başvurunun sahibi dükkânını döşerse, reddedildiğinde emeği
     * çöpe giderdi — ve caddede olmayan bir dükkânın ışığını ayarlamak zaten
     * anlamsız.
     */
    private MarketplaceListing requireOwned(UUID listingId, UUID userId) {
        MarketplaceListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("Stant bulunamadı"));
        if (!listing.getOwner().getId().equals(userId)) {
            throw ApiException.forbidden("Bu mağaza size ait değil");
        }
        if (listing.getStatus() != ListingStatus.APPROVED) {
            throw ApiException.badRequest("Mağaza stüdyosu yalnız onaylanmış stantlar için açılır");
        }
        return listing;
    }
}
