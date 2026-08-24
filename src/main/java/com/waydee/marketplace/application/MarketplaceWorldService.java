package com.waydee.marketplace.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.storage.MediaUrls;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceListing;
import com.waydee.marketplace.domain.MarketplaceStatus;
import com.waydee.marketplace.domain.StoreProduct;
import com.waydee.marketplace.domain.StoreSettings;
import com.waydee.marketplace.infrastructure.MarketplaceListingRepository;
import com.waydee.marketplace.infrastructure.MarketplaceRepository;
import com.waydee.marketplace.infrastructure.StoreProductRepository;
import com.waydee.marketplace.infrastructure.StoreSettingsRepository;
import com.waydee.social.api.dto.SocialDtos.PostResponse;
import com.waydee.social.application.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <b>3B pazar yeri evreni</b> — onaylı stantların gezilebilir bir caddeye
 * yerleştirilmiş hâli.
 *
 * <h2>Neden ayrı bir servis?</h2>
 * <p>{@link MarketplaceMapService} aynı stantları <b>gerçek dünya
 * koordinatlarında</b> (GeoJSON, Mapbox) verir. Burada üretilen şey bambaşka
 * bir uzaydır: metre cinsinden, <b>yerel</b> bir sahne koordinat sistemi.
 * İkisini tek serviste toplamak "hangi x hangi uzayda?" sorusunu her çağrıda
 * yeniden sordururdu.
 *
 * <h2>Yerleşim kuralı</h2>
 * <p>Stantlar caddenin iki yakasına <b>sırayla</b> dizilir: 0 → sağ, 1 → sol,
 * 2 → sağ… Bu yüzden karşılıklı iki mağaza aynı sıradadır ve cadde
 * <b>simetrik</b> görünür.
 *
 * <p>🔴 Sıra {@code spotIndex}'ten gelir, listenin sırasından DEĞİL. Stant
 * indeksi kalıcıdır ("yeni stant eskileri oynatmaz" — vault altın kuralı); liste
 * sırası ise beğeniyle değişir. Liste sırasını kullansaydık, biri bir stanta
 * beğeni bastığında <b>bütün cadde yer değiştirirdi</b> ve kullanıcı dün
 * gittiği dükkânı bugün bulamazdı.
 *
 * <p>⚠️ {@code spotIndex} boş olabilir (eski kayıtlar): o durumda listedeki
 * sıraya düşülür — mağazayı hiç göstermemektense kararsız bir yere koymak
 * yeğdir.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceWorldService {

    /** Caddenin yarı genişliği (metre) — mağaza cephesinin merkeze uzaklığı. */
    private static final double STREET_HALF_WIDTH = 9.0;

    /** İki sıra arasındaki mesafe (metre). Mağaza cephesi ~8 m. */
    private static final double ROW_SPACING = 14.0;

    /**
     * Bir mağazanın raflarına konacak azami gönderi sayısı.
     *
     * <p>⚠️ Bilinçli olarak küçük: her gönderi sahnede bir doku (texture)
     * demektir ve dokular GPU belleğinde birikir. 40 mağazalık bir caddede
     * mağaza başına 12 görsel bile 480 doku eder; bunu 100'e çıkarmak orta
     * seviye bir telefonu düşürürdü.
     */
    private static final int SHELF_CAPACITY = 12;

    private final MarketplaceRepository marketplaceRepository;
    private final MarketplaceListingRepository listingRepository;
    private final StoreSettingsRepository settingsRepository;
    private final StoreProductRepository productRepository;
    private final PostService postService;

    /**
     * Bir pazar yerinin gezilebilir evrenini kurar.
     *
     * @param slug     pazar yerinin kısa adı
     * @param viewerId bakan kişi (gizli hesapların gönderileri onun için
     *                 süzülsün diye {@link PostService}'e geçirilir — süzme
     *                 kararı orada, bu serviste değil)
     */
    @Transactional(readOnly = true)
    public WorldView build(String slug, UUID viewerId) {
        /* ⚠️ "Yayında" = OPEN **veya** CLOSED. CLOSED yalnız BAŞVURUYA kapalıdır;
           içindeki mağazalar durur ve gezilebilir olmalıdır. Yalnız OPEN'ı
           kabul etseydik, başvuru penceresi kapanan bir pazarın bütün caddesi
           bir gecede yok olurdu. Süzgeç `findPublic()` ile aynı. */
        Marketplace market = marketplaceRepository.findBySlug(slug)
                .filter(m -> m.getStatus() == MarketplaceStatus.OPEN
                        || m.getStatus() == MarketplaceStatus.CLOSED)
                .orElseThrow(() -> ApiException.notFound("Pazar yeri bulunamadı"));

        List<MarketplaceListing> approved = listingRepository.findApproved(market.getId());

        /*
         * 🔴 15 Ağu 2026 — STÜDYO VERİSİ TEK SORGUDA (N+1 tuzağı).
         *
         * Mağaza başına ayrı sorgu atmak 40 dükkânlık bir caddede 80 gidiş-dönüş
         * demekti (ayar + ürünler). Evren ucu zaten TEK istekte tüm sahneyi
         * döndürüyor; buradaki maliyet doğrudan sahnenin açılma süresidir.
         */
        List<UUID> ids = approved.stream().map(MarketplaceListing::getId).toList();
        Map<UUID, StoreSettings> settingsById = ids.isEmpty() ? Map.of()
                : settingsRepository.findByListingIdIn(ids).stream()
                        .collect(Collectors.toMap(StoreSettings::getListingId, s -> s));
        Map<UUID, List<StoreProduct>> productsById = ids.isEmpty() ? Map.of()
                : productRepository.findVisibleForListings(ids).stream()
                        .collect(Collectors.groupingBy(StoreProduct::getListingId));

        /* Raftaki POST ürünlerinin görselleri de TEK seferde çözülür. */
        Map<UUID, String> postImages = resolvePostImages(productsById, viewerId);

        List<StoreView> stores = new java.util.ArrayList<>(approved.size());
        for (int i = 0; i < approved.size(); i++) {
            MarketplaceListing listing = approved.get(i);
            int slot = listing.getSpotIndex() != null ? listing.getSpotIndex() : i;
            stores.add(toStore(listing, slot, viewerId,
                    settingsById.get(listing.getId()),
                    productsById.getOrDefault(listing.getId(), List.of()),
                    postImages));
        }
        /* Sahnede çizim sırası caddede yürüme sırası olsun — istemci bunu
           yeniden sıralamak zorunda kalmasın. */
        stores.sort(java.util.Comparator.comparingInt(StoreView::slot));

        return new WorldView(
                market.getId(),
                market.getSlug(),
                market.getName(),
                market.getTagline(),
                market.getAccentColor(),
                STREET_HALF_WIDTH,
                ROW_SPACING,
                stores);
    }

    /**
     * Raftaki tüm POST ürünlerinin ilk görselini <b>tek çağrıda</b> çözer.
     *
     * <p>⚠️ Görünürlük kararı {@link PostService}'in kendisine bırakılır
     * (gizli hesap süzgeci orada). Burada yalnız kimlikten adrese eşleme var.
     */
    private Map<UUID, String> resolvePostImages(Map<UUID, List<StoreProduct>> productsById, UUID viewerId) {
        List<UUID> postIds = productsById.values().stream()
                .flatMap(List::stream)
                .map(StoreProduct::getPostId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return Map.of();
        }
        /*
         * 🔴 Gönderi görselleri `PostResponse` üzerinden geldiği için normal
         * (302'li) adreslerdir; 3B'de doku olarak kullanılacaklarına göre
         * onlar da AYNI ORİJİNDEN akmalı — gerekçe `MediaUrls.streamed`.
         * ⚠️ Bu satır atlanırsa yalnız CUSTOM ürünlerin fotoğrafı görünür,
         * profilden eklenenler görünmez: hatanın en sinsi hâli.
         */
        Map<UUID, String> out = new java.util.HashMap<>();
        postService.firstImageUrls(postIds, viewerId)
                .forEach((id, url) -> out.put(id, asStreamed(url)));
        return out;
    }

    /** Hazır bir medya adresini aynı orijinden akıtılan hâline çevirir. */
    private static String asStreamed(String url) {
        if (url == null || url.contains("stream=1")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "stream=1";
    }

    private StoreView toStore(MarketplaceListing listing, int slot, UUID viewerId,
                              StoreSettings settings, List<StoreProduct> products,
                              Map<UUID, String> postImages) {
        /* Çift indeks sağ yaka, tek indeks sol yaka; sıra ikiye bölümdür. */
        boolean right = slot % 2 == 0;
        int row = slot / 2;

        /*
         * 🔴 RAF ARTIK STÜDYODAN GELİYOR.
         *
         * Eskiden raf **koşulsuz** sahibinin son gönderileriydi; sahibinin
         * neyi vitrine koyacağı üzerinde hiçbir denetimi yoktu. Artık stüdyoda
         * seçtiği ürünler çiziliyor.
         *
         * ⚠️ Stüdyoyu hiç açmamış mağazalar için ESKİ DAVRANIŞ korunuyor
         * (son gönderiler). Aksi hâlde bu değişiklik, bugün caddede duran
         * bütün dükkânların raflarını bir anda **boşaltırdı**.
         */
        List<ShelfItem> shelf = products.isEmpty()
                ? legacyShelf(listing, viewerId)
                : products.stream().limit(SHELF_CAPACITY)
                        .map(p -> toShelfItem(p, postImages))
                        /* Görseli çözülemeyen ürün (silinmiş/gizli gönderi)
                           rafa konmaz — boş çerçeve rafı bozuk gösterirdi. */
                        .filter(item -> item.imageUrl() != null)
                        .toList();

        return new StoreView(
                listing.getId(),
                slot,
                row,
                right ? "RIGHT" : "LEFT",
                /* Cephe merkezi: yaka işareti × yarı genişlik, sıra × aralık. */
                (right ? STREET_HALF_WIDTH : -STREET_HALF_WIDTH),
                row * ROW_SPACING,
                /* Tabelada stüdyodaki ad varsa o yazar. */
                settings != null && settings.getDisplayName() != null && !settings.getDisplayName().isBlank()
                        ? settings.getDisplayName()
                        : listing.getTitle(),
                listing.getTagline(),
                listing.getCategory() != null ? listing.getCategory().name() : null,
                listing.getCategory() != null ? listing.getCategory().label() : null,
                listing.getOwner().getUsername(),
                /* 🔴 `streamed`: 3B dokuları CORS ister, S3 vermiyor — gerekçe MediaUrls. */
                MediaUrls.streamed(listing.getLogoMediaId()),
                MediaUrls.streamed(listing.getCoverMediaId()),
                listing.isFeatured(),
                listing.getLikeCount(),
                shelf,
                toStudio(settings));
    }

    /** Stüdyo hiç açılmamışsa varsayılanlar — sahne her koşulda çizilebilmeli. */
    private static StudioView toStudio(StoreSettings s) {
        if (s == null) {
            return new StudioView(null, false, true, null, false, 0,
                    "#ffd9a0", 100, "#83bf6e", "#111315");
        }
        return new StudioView(
                /* ⚠️ Televizyon KAPALIYSA adres hiç gönderilmez: istemci
                   yalnızca url varlığına bakıp video elemanı kurmasın —
                   kapalı bir televizyon için 60 MB indirmek anlamsız. */
                s.isTvEnabled() ? MediaUrls.streamed(s.getVideoMediaId()) : null,
                s.isTvEnabled() && s.getVideoMediaId() != null,
                s.isTvMuted(),
                s.isMusicEnabled() ? MediaUrls.of(s.getMusicMediaId()) : null,
                s.isMusicEnabled() && s.getMusicMediaId() != null,
                s.getMusicVolume(),
                s.getLightColor(),
                s.getLightIntensity(),
                s.getAccentColor(),
                s.getFacadeColor());
    }

    /** Stüdyo öncesi davranış: sahibinin son görselli gönderileri. */
    private List<ShelfItem> legacyShelf(MarketplaceListing listing, UUID viewerId) {
        return postService.byAuthor(listing.getOwner().getId(), viewerId, 0, SHELF_CAPACITY)
                .content().stream()
                /* Görselsiz gönderi rafa konmaz: boş bir çerçeve, rafın
                   bozuk olduğu izlenimi verirdi. */
                .filter(p -> p.mediaUrls() != null && !p.mediaUrls().isEmpty())
                .map(MarketplaceWorldService::toShelfItem)
                .toList();
    }

    /**
     * Stüdyo ürününü rafa çevirir.
     *
     * <p>⚠️ POST kaynaklı üründe görsel <b>gönderiden</b> okunur; stüdyo onu
     * kopyalamaz ki kullanıcı fotoğrafı değiştirince raf da güncellensin.
     * Gönderi görünmezse (gizli hesap, silinmiş) ürün görselsiz kalır ve
     * istemci onu çizmez.
     *
     * <p>🔴 Görsel haritası <b>dışarıdan</b> gelir. İlk yazımda burada
     * {@code postService} çağrılıyordu — yani ürün başına bir sorgu: az önce
     * ayar/ürün için kaldırılan N+1'in aynısını raf seviyesinde geri getiriyordu.
     */
    private static ShelfItem toShelfItem(StoreProduct p, Map<UUID, String> postImages) {
        String image = p.getPostId() != null
                ? postImages.get(p.getPostId())
                : MediaUrls.streamed(p.getImageMediaId());
        return new ShelfItem(p.getPostId(), image, p.getTitle(), 0);
    }

    private static ShelfItem toShelfItem(PostResponse post) {
        return new ShelfItem(
                post.id(),
                post.mediaUrls().getFirst(),
                post.caption(),
                post.likeCount());
    }

    /** Gezilebilir evren — cadde ölçüleri + mağazalar. */
    public record WorldView(
            UUID id,
            String slug,
            String name,
            String tagline,
            String accentColor,
            /** Caddenin yarı genişliği (metre) — istemci zemini buna göre serer. */
            double streetHalfWidth,
            /** İki sıra arası mesafe (metre). */
            double rowSpacing,
            List<StoreView> stores
    ) {
    }

    /**
     * Caddedeki tek bir mağaza.
     *
     * <p>{@code x}/{@code z} <b>metre</b> cinsindendir ve sahnenin merkezine
     * görelidir; enlem/boylam DEĞİLDİR. Karıştırılırsa mağazalar Gine
     * Körfezi'ne düşer.
     */
    public record StoreView(
            UUID id,
            int slot,
            int row,
            /** "LEFT" ya da "RIGHT" — cephenin baktığı yön buradan türer. */
            String side,
            double x,
            double z,
            String title,
            String tagline,
            String category,
            String categoryLabel,
            String ownerUsername,
            String logoUrl,
            String coverUrl,
            boolean featured,
            int likeCount,
            List<ShelfItem> shelf,
            /** Sahibinin stüdyodan yaptığı özelleştirmeler — hiç açmadıysa varsayılanlar. */
            StudioView studio
    ) {
    }

    /**
     * Mağazanın 3B sunumu: kasa arkasındaki televizyon, müzik ve ışık.
     *
     * <p>⚠️ {@code videoUrl}/{@code musicUrl} <b>kapalıyken null gelir</b>.
     * İstemci yalnız adres varlığına bakıp eleman kurar; kapalı bir televizyon
     * için 60 MB indirmek anlamsız olurdu.
     */
    public record StudioView(
            String videoUrl,
            boolean tvOn,
            boolean tvMuted,
            String musicUrl,
            boolean musicOn,
            int musicVolume,
            String lightColor,
            int lightIntensity,
            String accentColor,
            String facadeColor
    ) {
    }

    /** Rafa konan tek bir gönderi. */
    public record ShelfItem(
            UUID postId,
            String imageUrl,
            String caption,
            int likeCount
    ) {
    }
}
