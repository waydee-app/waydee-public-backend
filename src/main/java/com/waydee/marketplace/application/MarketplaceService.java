package com.waydee.marketplace.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.common.geo.GeoUtils;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.marketplace.api.dto.MarketplaceDtos.MarketplaceRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.MarketplaceResponse;
import com.waydee.marketplace.application.event.MarketplaceChangedEvent;
import com.waydee.marketplace.domain.ListingStatus;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceListing;
import com.waydee.marketplace.domain.MarketplaceStatus;
import com.waydee.marketplace.infrastructure.MarketplaceListingRepository;
import com.waydee.marketplace.infrastructure.MarketplaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pazar yeri yönetimi (admin) + okuma (üye/vitrin).
 *
 * Stant iş akışı ayrı serviste ({@link ListingService}) durur; burası yalnız
 * pazarın kendisiyle ilgilenir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final MarketplaceRepository marketplaceRepository;
    private final MarketplaceListingRepository listingRepository;
    private final FormSchemaService formSchemaService;
    private final AuditRecorder auditRecorder;
    private final DomainEventPublisher eventPublisher;

    // ------------------------------------------------------------ okuma

    /** Üye/vitrin listesi — taslak ve arşiv görünmez. */
    @Transactional(readOnly = true)
    public List<MarketplaceResponse> listPublic(UUID viewerId) {
        Instant now = Instant.now();
        return marketplaceRepository.findPublic().stream()
                .map(m -> toResponse(m, viewerId, now))
                .toList();
    }

    /** Yönetim listesi — her durum dahil. */
    @Transactional(readOnly = true)
    public List<MarketplaceResponse> listAll() {
        Instant now = Instant.now();
        return marketplaceRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(m -> toResponse(m, null, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceResponse get(UUID id, UUID viewerId) {
        return toResponse(require(id), viewerId, Instant.now());
    }

    @Transactional(readOnly = true)
    public MarketplaceResponse getBySlug(String slug, UUID viewerId) {
        Marketplace m = marketplaceRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("Pazar yeri bulunamadı"));
        // Taslak ve arşiv yalnız yönetimde görünür; üyeye "yok" denir.
        if (viewerId != null && (m.getStatus() == MarketplaceStatus.DRAFT
                || m.getStatus() == MarketplaceStatus.ARCHIVED)) {
            throw ApiException.notFound("Pazar yeri bulunamadı");
        }
        return toResponse(m, viewerId, Instant.now());
    }

    // ------------------------------------------------------------ yönetim

    @Transactional
    public MarketplaceResponse create(MarketplaceRequest request, AuthenticatedUser actor) {
        Polygon boundary = GeoUtils.polygon(request.ring().stream()
                .map(p -> new double[]{p.get(0), p.get(1)}).toList());
        String slug = resolveSlug(request.slug(), request.name(), null);

        Marketplace marketplace = new Marketplace(slug, request.name().trim(), boundary);
        apply(marketplace, request);
        Marketplace saved = marketplaceRepository.save(marketplace);

        auditRecorder.record(actor.id(), actor.username(), "MARKETPLACE_CREATED", "MARKETPLACE",
                saved.getId().toString(), Map.of("name", saved.getName(), "slug", saved.getSlug()), null);
        eventPublisher.publish(new MarketplaceChangedEvent(saved.getId(), "CREATED"));
        log.info("Pazar yeri oluşturuldu: {} ({})", saved.getName(), saved.getSlug());
        return toResponse(saved, null, Instant.now());
    }

    @Transactional
    public MarketplaceResponse update(UUID id, MarketplaceRequest request, AuthenticatedUser actor) {
        Marketplace marketplace = require(id);
        if (request.ring() != null && !request.ring().isEmpty()) {
            marketplace.applyBoundary(GeoUtils.polygon(request.ring().stream()
                    .map(p -> new double[]{p.get(0), p.get(1)}).toList()));
        }
        if (request.name() != null && !request.name().isBlank()) {
            marketplace.setName(request.name().trim());
        }
        if (request.slug() != null && !request.slug().isBlank()) {
            marketplace.setSlug(resolveSlug(request.slug(), marketplace.getName(), marketplace.getId()));
        }
        apply(marketplace, request);

        auditRecorder.record(actor.id(), actor.username(), "MARKETPLACE_UPDATED", "MARKETPLACE",
                id.toString(), Map.of("status", marketplace.getStatus().name()), null);
        eventPublisher.publish(new MarketplaceChangedEvent(id, "UPDATED"));
        return toResponse(marketplace, null, Instant.now());
    }

    /**
     * Arşivler. SİLMEZ — stantlar ve başvuru geçmişi korunur; pazar yalnız
     * haritadan ve listelerden düşer, gerekirse geri açılabilir.
     */
    @Transactional
    public void archive(UUID id, AuthenticatedUser actor) {
        Marketplace marketplace = require(id);
        marketplace.setStatus(MarketplaceStatus.ARCHIVED);
        auditRecorder.record(actor.id(), actor.username(), "MARKETPLACE_ARCHIVED", "MARKETPLACE",
                id.toString(), null, null);
        eventPublisher.publish(new MarketplaceChangedEvent(id, "REMOVED"));
    }

    // ------------------------------------------------------------ yardımcılar

    /** Üye yüzeyi için: taslak ve arşiv "yok" sayılır. */
    public Marketplace requirePublic(UUID id) {
        Marketplace m = require(id);
        if (m.getStatus() == MarketplaceStatus.DRAFT || m.getStatus() == MarketplaceStatus.ARCHIVED) {
            throw ApiException.notFound("Pazar yeri bulunamadı");
        }
        return m;
    }

    public Marketplace require(UUID id) {
        return marketplaceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pazar yeri bulunamadı"));
    }

    private void apply(Marketplace m, MarketplaceRequest r) {
        if (r.tagline() != null) {
            m.setTagline(r.tagline().isBlank() ? null : r.tagline().trim());
        }
        if (r.description() != null) {
            m.setDescription(r.description().isBlank() ? null : r.description().trim());
        }
        if (r.accentColor() != null && !r.accentColor().isBlank()) {
            m.setAccentColor(r.accentColor());
        }
        if (r.coverMediaId() != null) {
            m.setCoverMediaId(r.coverMediaId());
        }
        if (r.status() != null) {
            m.setStatus(MarketplaceStatus.valueOf(r.status()));
        }
        /*
         * 🔴 15 Ağu 2026 — SÜRE ARTIK GÜN CİNSİNDEN (kullanıcı isteği:
         * "pazar başlangıç tarihi ve süresi kaç gün açık kalacak").
         *
         * Eskiden `opensAt` ve `closesAt` **iki bağımsız alan** olarak
         * yazılıyordu. Artık admin başlangıç + gün giriyor; kapanış anını
         * entity TÜRETİYOR (`schedule`). Böylece "başlangıç değişti ama
         * kapanış eski kaldı" durumu imkânsız — vault'taki
         * `PAYMENT_RETURN_URL` dersinin aynısı, sadece takvimde.
         */
        m.schedule(r.opensAt(), r.durationDays());
        m.setMaxListings(r.maxListings());
        if (r.autoApprove() != null) {
            m.setAutoApprove(r.autoApprove());
        }
        if (r.kind() != null) {
            m.setKind(com.waydee.marketplace.domain.MarketplaceKind.valueOf(r.kind()));
        }
        if (r.formSchema() != null) {
            m.setFormSchema(formSchemaService.serialize(r.formSchema()));
        }
        if (r.applicationNote() != null) {
            m.setApplicationNote(r.applicationNote().isBlank() ? null : r.applicationNote().trim());
        }
        /*
         * ⚠️ Kapanış artık türetildiği için "kapanış açılıştan önce" durumu
         * **oluşamaz** (gün sayısı pozitif doğrulanıyor). Eski kontrol
         * kaldırıldı; yerine süre doğrulaması DTO'da (`@Min(1)`).
         */
    }

    private MarketplaceResponse toResponse(Marketplace m, UUID viewerId, Instant now) {
        String myStatus = null;
        UUID myListingId = null;
        if (viewerId != null) {
            Optional<MarketplaceListing> mine = listingRepository.findActiveByOwner(m.getId(), viewerId);
            if (mine.isPresent()) {
                myStatus = mine.get().getStatus().name();
                myListingId = mine.get().getId();
            } else {
                // Reddedilen başvuru da kullanıcıya gösterilmeli ("düzenle ve tekrar gönder").
                myStatus = listingRepository.findByOwnerIdOrderBySubmittedAtDesc(viewerId).stream()
                        .filter(l -> l.getMarketplaceId().equals(m.getId()))
                        .findFirst()
                        .map(l -> {
                            return l.getStatus().name();
                        })
                        .orElse(null);
            }
        }
        return MarketplaceResponse.from(m, m.acceptsApplications(now), myStatus, myListingId);
    }

    /**
     * Kısa ad üretir/doğrular. Boşsa isimden türetilir; çakışırsa sonuna sayı eklenir.
     * Türkçe karakterler ASCII'ye indirgenir (URL'de bozulmasın).
     */
    private String resolveSlug(String requested, String name, UUID selfId) {
        String base = (requested != null && !requested.isBlank()) ? requested : slugify(name);
        if (base.isBlank()) {
            base = "pazar";
        }
        String candidate = base;
        int suffix = 2;
        while (true) {
            Optional<Marketplace> existing = marketplaceRepository.findBySlug(candidate);
            if (existing.isEmpty() || existing.get().getId().equals(selfId)) {
                return candidate;
            }
            candidate = base + "-" + suffix++;
            if (suffix > 500) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Kısa ad üretilemedi");
            }
        }
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String s = value.toLowerCase(new Locale("tr", "TR"))
                .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
                .replace('ş', 's').replace('ö', 'o').replace('ç', 'c');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** Onay/ret sonrası sayaç — {@link ListingService} çağırır. */
    void adjustListingCount(UUID marketplaceId, int delta) {
        marketplaceRepository.adjustListingCount(marketplaceId, delta);
    }

    long pendingCount() {
        return listingRepository.countByStatus(ListingStatus.PENDING);
    }
}
