package com.waydee.marketplace.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingResponse;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ReviewRequest;
import com.waydee.marketplace.application.event.MarketplaceChangedEvent;
import com.waydee.marketplace.domain.ListingCategory;
import com.waydee.marketplace.domain.ListingLike;
import com.waydee.marketplace.domain.ListingStage;
import com.waydee.marketplace.domain.ListingStatus;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceKind.Field;
import com.waydee.marketplace.domain.MarketplaceListing;
import com.waydee.marketplace.infrastructure.ListingLikeRepository;
import com.waydee.marketplace.infrastructure.MarketplaceListingRepository;
import com.waydee.marketplace.infrastructure.MarketplaceRepository;
import com.waydee.moderation.application.RestrictionService;
import com.waydee.moderation.domain.RestrictedAction;
import com.waydee.social.application.HtmlSanitizer;
import com.waydee.social.application.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stant (başvuru) iş akışı.
 *
 * <p><b>Kurallar:</b>
 * <ul>
 *   <li>Bir üye aynı pazara yalnız BİR aktif başvuru yapabilir (DB'de kısmi
 *       UNIQUE indeks; burada da kontrol edilir ki hata mesajı anlamlı olsun).</li>
 *   <li>Onay yalnız ADMIN'dedir; {@code autoApprove} açıksa sunucu kendi onaylar.</li>
 *   <li>Onaylanan stant pazar yerinin İÇİNDE bir noktaya oturtulur
 *       ({@link StallPlacement}); indeks kalıcıdır, sonraki onaylar eskileri oynatmaz.</li>
 *   <li>Kontenjan sayacı ATOMİK artar — eşzamanlı onayda aşılmaz.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final MarketplaceRepository marketplaceRepository;
    private final MarketplaceListingRepository listingRepository;
    private final ListingLikeRepository likeRepository;
    private final UserRepository userRepository;
    private final MarketplaceService marketplaceService;
    private final MediaService mediaService;
    private final HtmlSanitizer htmlSanitizer;
    private final FormSchemaService formSchemaService;
    private final RestrictionService restrictionService;
    private final com.waydee.identity.application.EmailVerificationService emailVerificationService;
    private final AuditRecorder auditRecorder;
    private final DomainEventPublisher eventPublisher;

    // ------------------------------------------------------------ üye: başvuru

    @Transactional
    public ListingResponse apply(UUID marketplaceId, UUID userId, ListingRequest request) {
        restrictionService.assertAllowed(userId, RestrictedAction.POST);
        // Stant başvurusu adminin göreceği bir iletişim kaydıdır; adres doğrulanmış olmalı.
        emailVerificationService.assertVerified(userId);
        Marketplace marketplace = marketplaceService.require(marketplaceId);
        if (!marketplace.acceptsApplications(Instant.now())) {
            throw ApiException.badRequest(marketplace.isFull()
                    ? "Bu pazar yerinde kontenjan doldu"
                    : "Bu pazar yeri şu anda başvuru almıyor");
        }
        // Reddedilmiş bir başvuru varsa YENİSİ AÇILMAZ; mevcut kayıt güncellenir.
        Optional<MarketplaceListing> previous = listingRepository
                .findByOwnerIdOrderBySubmittedAtDesc(userId).stream()
                .filter(l -> l.getMarketplaceId().equals(marketplaceId))
                .findFirst();
        if (previous.isPresent() && previous.get().getStatus() == ListingStatus.APPROVED) {
            throw ApiException.badRequest("Bu pazar yerinde zaten bir stantın var");
        }
        if (previous.isPresent() && previous.get().getStatus() == ListingStatus.PENDING) {
            throw ApiException.badRequest("Başvurun zaten değerlendirmede");
        }

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        MarketplaceListing listing = previous.orElseGet(() -> new MarketplaceListing(
                marketplaceId, owner, resolveTitle(request, owner),
                blankToNull(request.description()), resolveCategory(request)));
        applyContent(listing, request, userId, marketplace);
        listing.resubmit();

        MarketplaceListing saved = listingRepository.save(listing);

        // Otomatik onay açıksa yönetim beklemeden yerine oturur.
        if (marketplace.isAutoApprove()) {
            place(saved, marketplace, null, "Otomatik onay");
        }

        auditRecorder.record(userId, owner.getUsername(), "MARKETPLACE_LISTING_SUBMITTED", "LISTING",
                saved.getId().toString(), Map.of("marketplace", marketplace.getName()), null);
        return toResponse(saved, marketplace, userId);
    }

    @Transactional
    public ListingResponse updateMine(UUID listingId, UUID userId, ListingRequest request) {
        MarketplaceListing listing = requireOwned(listingId, userId);
        /*
         * ═══════════════════════════════════════════════════════════════
         * 🔴 15 Ağu 2026 — DÜZENLEME SESSİZCE KAYBOLUYORDU (yaşanmış hata)
         *
         * Bildirilen: *"düzenle kısmında logo güncelleyince istek geliyor,
         * admin onaylıyor, ama görsel güncellenmiyor."*
         *
         * <b>Kök neden:</b> `adjustListingCount` sorgusu
         * {@code @Modifying(clearAutomatically = true)} taşıyor — çalıştığı anda
         * <b>persistence context'i temizler</b> ve elimizdeki {@code listing}
         * nesnesi <b>DETACHED</b> olur. Eskiden bu çağrı `applyContent`'ten
         * ÖNCE yapılıyordu; dolayısıyla sonraki bütün içerik değişiklikleri
         * (logo · tanıtım · website · telefon) kopmuş bir nesneye yazılıyor ve
         * <b>hiç kaydedilmiyordu</b>.
         *
         * <b>Neden fark edilmedi:</b> aynı sorgu {@code flushAutomatically = true}
         * de taşıyor, yani ondan ÖNCE yapılan {@code resubmit()} (durum →
         * PENDING) <b>kaydediliyordu</b>. Sonuç: admin panelinde istek
         * görünüyor, onaylanıyor, ama içerik eski kalıyor — belirtiyi bu kadar
         * kafa karıştırıcı yapan şey buydu.
         *
         * <b>Çözüm:</b> sıra tersine çevrildi. Önce içerik uygulanır ve
         * <b>açıkça kaydedilir</b>, yanıt üretilir; sayaç düşümü ile olay
         * yayını <b>en sona</b> alınır. Böylece bağlam temizlendiğinde
         * yazılacak bir şey kalmamış olur.
         *
         * ⚠️ Yanıt da temizlikten ÖNCE üretilir: detached bir nesneden tembel
         * alan okumak `LazyInitializationException` doğurabilir.
         * ═══════════════════════════════════════════════════════════════
         */
        boolean wasApproved = listing.getStatus() == ListingStatus.APPROVED;

        Marketplace mp = marketplaceService.require(listing.getMarketplaceId());
        applyContent(listing, request, userId, mp);
        // Onaylı stant düzenlenince yeniden incelemeye düşer — içerik
        // onaydan sonra sessizce değiştirilememeli.
        listing.resubmit();

        /* Açık `save`: sıra değişse de niyet kodda görünür kalsın. */
        MarketplaceListing saved = listingRepository.save(listing);
        ListingResponse response = toResponse(saved, mp, userId);

        if (wasApproved) {
            marketplaceService.adjustListingCount(listing.getMarketplaceId(), -1);
            eventPublisher.publish(new MarketplaceChangedEvent(listing.getMarketplaceId(), "LISTING_REMOVED"));
        }
        return response;
    }

    @Transactional
    public void withdraw(UUID listingId, UUID userId) {
        MarketplaceListing listing = requireOwned(listingId, userId);
        boolean wasApproved = listing.getStatus() == ListingStatus.APPROVED;
        listing.withdraw();
        if (wasApproved) {
            marketplaceService.adjustListingCount(listing.getMarketplaceId(), -1);
            eventPublisher.publish(new MarketplaceChangedEvent(listing.getMarketplaceId(), "LISTING_REMOVED"));
        }
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> myListings(UUID userId) {
        return listingRepository.findByOwnerIdOrderBySubmittedAtDesc(userId).stream()
                .map(l -> toResponse(l, marketplaceRepository.findById(l.getMarketplaceId()).orElse(null), userId))
                .toList();
    }

    // ------------------------------------------------------------ okuma

    /** Bir pazardaki onaylı stantlar (vitrin ızgarası + harita). */
    @Transactional(readOnly = true)
    public List<ListingResponse> approved(UUID marketplaceId, UUID viewerId) {
        Marketplace marketplace = marketplaceService.require(marketplaceId);
        List<MarketplaceListing> listings = listingRepository.findApproved(marketplaceId);
        Set<UUID> liked = likedIds(viewerId, listings);
        return listings.stream().map(l -> toResponse(l, marketplace, viewerId, liked)).toList();
    }

    @Transactional
    public ListingResponse detail(UUID listingId, UUID viewerId) {
        MarketplaceListing listing = listingRepository.findWithOwnerById(listingId)
                .orElseThrow(() -> ApiException.notFound("Stant bulunamadı"));
        boolean mine = viewerId != null && viewerId.equals(listing.getOwner().getId());
        if (!listing.isVisible() && !mine) {
            // Onaysız stant sahibinden başkasına gösterilmez (varlığı da sızmaz).
            throw ApiException.notFound("Stant bulunamadı");
        }
        if (!mine) {
            listingRepository.incrementViewCount(listingId);
        }
        return toResponse(listing, marketplaceRepository.findById(listing.getMarketplaceId()).orElse(null), viewerId);
    }

    // ------------------------------------------------------------ beğeni

    @Transactional
    public ListingResponse like(UUID listingId, UUID userId, boolean liked) {
        MarketplaceListing listing = listingRepository.findWithOwnerById(listingId)
                .orElseThrow(() -> ApiException.notFound("Stant bulunamadı"));
        if (!listing.isVisible()) {
            throw ApiException.badRequest("Yayında olmayan stant beğenilemez");
        }
        ListingLike.Key key = new ListingLike.Key(listingId, userId);
        boolean exists = likeRepository.existsById(key);
        if (liked && !exists) {
            likeRepository.save(new ListingLike(listingId, userId));
            listingRepository.adjustLikeCount(listingId, 1);
        } else if (!liked && exists) {
            likeRepository.deleteById(key);
            listingRepository.adjustLikeCount(listingId, -1);
        }
        MarketplaceListing fresh = listingRepository.findWithOwnerById(listingId).orElseThrow();
        return toResponse(fresh, marketplaceRepository.findById(fresh.getMarketplaceId()).orElse(null), userId);
    }

    // ------------------------------------------------------------ yönetim

    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> review(UUID marketplaceId, String status, Pageable pageable) {
        ListingStatus target = status == null || status.isBlank()
                ? ListingStatus.PENDING : ListingStatus.valueOf(status);
        return PageResponse.from(
                listingRepository.findForReview(marketplaceId, target, pageable),
                l -> toResponse(l, marketplaceRepository.findById(l.getMarketplaceId()).orElse(null), null));
    }

    @Transactional
    public ListingResponse decide(UUID listingId, ReviewRequest request, AuthenticatedUser actor) {
        MarketplaceListing listing = listingRepository.findWithOwnerById(listingId)
                .orElseThrow(() -> ApiException.notFound("Stant bulunamadı"));
        Marketplace marketplace = marketplaceService.require(listing.getMarketplaceId());

        if ("APPROVED".equals(request.decision())) {
            if (listing.getStatus() == ListingStatus.APPROVED) {
                throw ApiException.badRequest("Bu stant zaten onaylı");
            }
            if (marketplace.isFull()) {
                throw ApiException.badRequest("Kontenjan dolu — önce sınırı yükseltin");
            }
            place(listing, marketplace, actor.id(), request.note());
        } else {
            boolean wasApproved = listing.getStatus() == ListingStatus.APPROVED;
            listing.reject(actor.id(), request.note());
            if (wasApproved) {
                marketplaceService.adjustListingCount(marketplace.getId(), -1);
            }
            eventPublisher.publish(new MarketplaceChangedEvent(marketplace.getId(), "LISTING_REMOVED"));
        }

        auditRecorder.record(actor.id(), actor.username(), "MARKETPLACE_LISTING_REVIEWED", "LISTING",
                listingId.toString(),
                Map.of("decision", request.decision(), "marketplace", marketplace.getName()), null);
        return toResponse(listing, marketplace, null);
    }

    @Transactional
    public ListingResponse setFeatured(UUID listingId, boolean featured, AuthenticatedUser actor) {
        MarketplaceListing listing = listingRepository.findWithOwnerById(listingId)
                .orElseThrow(() -> ApiException.notFound("Stant bulunamadı"));
        listing.setFeatured(featured);
        auditRecorder.record(actor.id(), actor.username(), "MARKETPLACE_LISTING_FEATURED", "LISTING",
                listingId.toString(), Map.of("featured", featured), null);
        eventPublisher.publish(new MarketplaceChangedEvent(listing.getMarketplaceId(), "UPDATED"));
        return toResponse(listing, marketplaceRepository.findById(listing.getMarketplaceId()).orElse(null), null);
    }

    public long pendingCount() {
        return listingRepository.countByStatus(ListingStatus.PENDING);
    }

    // ------------------------------------------------------------ iç yardımcılar

    /** Onayla + pazar yerinin içinde bir noktaya oturt. */
    private void place(MarketplaceListing listing, Marketplace marketplace, UUID reviewerId, String note) {
        int index = listingRepository.maxSpotIndex(marketplace.getId()) + 1;
        listing.approve(reviewerId, StallPlacement.forIndex(marketplace.getBoundary(), index), index, note);
        marketplaceService.adjustListingCount(marketplace.getId(), 1);
        eventPublisher.publish(new MarketplaceChangedEvent(marketplace.getId(), "LISTING_APPROVED"));
        log.info("Stant onaylandı: {} → {} (#{})", listing.getTitle(), marketplace.getName(), index);
    }

    private void applyContent(MarketplaceListing l, ListingRequest r, UUID userId) {
        applyContent(l, r, userId, null);
    }

    /**
     * @param marketplace null ise şema doğrulaması yapılmaz (yalnız iç çağrılar).
     *                    Dolu ise KAPALI alanlar temizlenir ve zorunlular kontrol edilir.
     */
    private void applyContent(MarketplaceListing l, ListingRequest r, UUID userId, Marketplace marketplace) {
        if (marketplace != null) {
            l.setCustomFields(formSchemaService.validateAndBuildCustomFields(marketplace, r));
            /*
             * KAPALI alanlar temizlenir: admin "fiyat sorma" dediyse gönderilen
             * fiyat kaydedilmez. İstemci formu şemadan çizse de istek doğrudan
             * atılabilir; karar sunucuda verilir.
             */
            l.setStartsAt(on(marketplace, Field.STARTS_AT) ? r.startsAt() : null);
            l.setEndsAt(on(marketplace, Field.ENDS_AT) ? r.endsAt() : null);
            l.setLocationLabel(on(marketplace, Field.LOCATION) ? blankToNull(r.locationLabel()) : null);
            l.setCapacity(on(marketplace, Field.CAPACITY) ? r.capacity() : null);
            l.setPrice(on(marketplace, Field.PRICE) ? r.price() : null);
            l.setCurrency(on(marketplace, Field.PRICE) ? (r.currency() == null ? "TRY" : r.currency()) : null);
            l.setConditionCode(on(marketplace, Field.CONDITION) ? blankToNull(r.conditionCode()) : null);
            l.setContactPhone(on(marketplace, Field.CONTACT_PHONE) ? blankToNull(r.contactPhone()) : null);
            if (on(marketplace, Field.GALLERY)
                    && r.galleryMediaIds() != null && !r.galleryMediaIds().isEmpty()) {
                // 🔒 Her görselin sahipliği doğrulanır.
                for (UUID id : r.galleryMediaIds()) {
                    mediaService.assertOwnedBy(id, userId);
                }
                l.setGalleryMediaIds(r.galleryMediaIds().toArray(new UUID[0]));
            } else {
                l.setGalleryMediaIds(null);
            }
        }
        applyBaseContent(l, r, userId, marketplace);
    }

    private boolean on(Marketplace m, Field f) {
        return formSchemaService.isFieldEnabled(m, f);
    }

    /**
     * Başlık/açıklama/kategori her türde vardır; geri kalanlar ŞEMAYA BAĞLIDIR.
     *
     * ⚠️ Bu alanlar (aşama, kuruluş yılı, ekip, web, e-posta, logo, kapak) eskiden
     * koşulsuz yazılıyordu; bir yürüyüş pazarında "ekip büyüklüğü" kapalı olmasına
     * rağmen istemci gönderirse kaydediliyordu (ölçüldü: teamSize=99 sızdı).
     * Artık kapalı olan alan HER ZAMAN null'a çekilir.
     */
    /**
     * Mağaza adı — form artık sormuyor, o hâlde <b>türetilir</b>.
     *
     * <p>Sıra: açıkça gönderilen başlık → kullanıcının görünen adı → kullanıcı
     * adı. Sonuncusu her zaman doludur, yani ad <b>hiçbir koşulda boş kalmaz</b>
     * (3B tabela ve liste kartı onu okuyor).
     *
     * <p>⚠️ 90 karaktere kırpılır: kolon sınırı bu ve uzun bir görünen ad
     * kaydı 500 ile düşürürdü.
     */
    private static String resolveTitle(ListingRequest r, User owner) {
        if (r.title() != null && !r.title().isBlank()) {
            return r.title().trim();
        }
        String fromUser = owner.getDisplayName() != null && !owner.getDisplayName().isBlank()
                ? owner.getDisplayName().trim()
                : owner.getUsername();
        return fromUser.length() > 90 ? fromUser.substring(0, 90) : fromUser;
    }

    /**
     * Kategori — yeni mağaza formu sormuyor.
     *
     * <p>⚠️ Varsayılan {@code OTHER}: kolon artık null kabul ediyor ama
     * {@code ListingResponse.from} kategoriyi <b>koşulsuz</b> okuyor
     * ({@code l.getCategory().name()}). Burada null bırakmak, listeyi açan
     * herkese NPE verirdi. Türü sonradan sahibi/admin daraltabilir.
     */
    private static ListingCategory resolveCategory(ListingRequest r) {
        return r.category() == null || r.category().isBlank()
                ? ListingCategory.OTHER
                : ListingCategory.valueOf(r.category());
    }

    private void applyBaseContent(MarketplaceListing l, ListingRequest r, UUID userId, Marketplace m) {
        /*
         * 🔴 15 Ağu 2026 — BAŞVURU DÖRT ALANA İNDİ; bu üçü artık gelmeyebilir.
         *
         * ⚠️ Mevcut değeri KORUYARAK yazıyoruz. Eskiden koşulsuz
         * `r.title().trim()` çağrılıyordu; yeni form başlık göndermediği için
         * bu satır ① NPE atardı ② atmasa bile, kabul edilmiş bir mağazanın
         * stüdyodan verdiği adı her yeniden başvuruda SİLERDİ.
         */
        if (r.title() != null && !r.title().isBlank()) {
            l.setTitle(r.title().trim());
        } else if (l.getTitle() == null || l.getTitle().isBlank()) {
            l.setTitle(resolveTitle(r, l.getOwner()));
        }
        if (r.description() != null) {
            l.setDescription(blankToNull(r.description()));
        }
        if (r.category() != null && !r.category().isBlank()) {
            l.setCategory(ListingCategory.valueOf(r.category()));
        }

        boolean noSchema = m == null;
        l.setTagline(noSchema || on(m, Field.TAGLINE) ? blankToNull(r.tagline()) : null);
        l.setStage(noSchema || on(m, Field.STAGE)
                ? (r.stage() == null || r.stage().isBlank() ? null : ListingStage.valueOf(r.stage()))
                : null);
        // 🔒 Yalnız http/https — kartta tıklanabilir zararlı bağlantı oluşamaz.
        l.setWebsite(noSchema || on(m, Field.WEBSITE)
                ? (r.website() == null || r.website().isBlank() ? null : htmlSanitizer.normalizeWebsite(r.website()))
                : null);
        l.setContactEmail(noSchema || on(m, Field.CONTACT_EMAIL) ? blankToNull(r.contactEmail()) : null);
        l.setFoundedYear(noSchema || on(m, Field.FOUNDED_YEAR) ? r.foundedYear() : null);
        l.setTeamSize(noSchema || on(m, Field.TEAM_SIZE) ? r.teamSize() : null);
        l.setLookingFor(noSchema || on(m, Field.LOOKING_FOR) ? blankToNull(r.lookingFor()) : null);

        // 🔒 Başkasının medyası kendi stantına basılamaz (avatar IDOR'unun aynısı).
        if (noSchema || on(m, Field.LOGO)) {
            if (r.logoMediaId() != null) {
                mediaService.assertOwnedBy(r.logoMediaId(), userId);
                l.setLogoMediaId(r.logoMediaId());
            }
        } else {
            l.setLogoMediaId(null);
        }
        if (noSchema || on(m, Field.COVER)) {
            if (r.coverMediaId() != null) {
                mediaService.assertOwnedBy(r.coverMediaId(), userId);
                l.setCoverMediaId(r.coverMediaId());
            }
        } else {
            l.setCoverMediaId(null);
        }
    }

    private MarketplaceListing requireOwned(UUID listingId, UUID userId) {
        MarketplaceListing listing = listingRepository.findWithOwnerById(listingId)
                .orElseThrow(() -> ApiException.notFound("Stant bulunamadı"));
        if (!listing.getOwner().getId().equals(userId)) {
            // 404: başkasının stant kimliğinin varlığı sızmasın.
            throw ApiException.notFound("Stant bulunamadı");
        }
        return listing;
    }

    private Set<UUID> likedIds(UUID viewerId, List<MarketplaceListing> listings) {
        if (viewerId == null || listings.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(likeRepository.likedListingIds(viewerId,
                listings.stream().map(MarketplaceListing::getId).toList()));
    }

    private ListingResponse toResponse(MarketplaceListing l, Marketplace m, UUID viewerId) {
        boolean liked = viewerId != null
                && likeRepository.existsById(new ListingLike.Key(l.getId(), viewerId));
        return toResponse(l, m, viewerId, liked ? Set.of(l.getId()) : Set.of());
    }

    private ListingResponse toResponse(MarketplaceListing l, Marketplace m, UUID viewerId, Set<UUID> liked) {
        return ListingResponse.from(l, m, liked.contains(l.getId()),
                viewerId != null && viewerId.equals(l.getOwner().getId()),
                formSchemaService.readCustomFields(l.getCustomFields()));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
