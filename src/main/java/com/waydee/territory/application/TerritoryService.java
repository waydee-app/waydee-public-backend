package com.waydee.territory.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.config.CacheConfig;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.common.geo.GeoJson;
import com.waydee.common.geo.GeoUtils;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.storage.MediaUrls;
import com.waydee.common.web.PageResponse;
import com.waydee.geo.application.PricingService;
import com.waydee.geo.application.ResolvedRegion;
import com.waydee.geo.infrastructure.CountryRepository;
import com.waydee.geo.infrastructure.DistrictRepository;
import com.waydee.geo.infrastructure.PricingZoneRepository;
import com.waydee.geo.infrastructure.ProvinceRepository;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserPlan;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.territory.api.dto.TerritoryDtos.AdminReserveRequest;
import com.waydee.territory.api.dto.TerritoryDtos.AdminTerritoryResponse;
import com.waydee.territory.api.dto.TerritoryDtos.AdminUpdateTerritoryRequest;
import com.waydee.territory.api.dto.TerritoryDtos.PurchaseRequest;
import com.waydee.territory.api.dto.TerritoryDtos.QuoteRequest;
import com.waydee.territory.api.dto.TerritoryDtos.QuoteResponse;
import com.waydee.territory.api.dto.TerritoryDtos.TerritoryResponse;
import com.waydee.territory.application.event.TerritoryPurchasedEvent;
import com.waydee.territory.application.event.TerritoryRemovedEvent;
import com.waydee.territory.application.event.TerritoryStyleChangedEvent;
import com.waydee.territory.domain.Purchase;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.domain.TerritoryStatus;
import com.waydee.territory.infrastructure.PurchaseRepository;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TerritoryProperties.class)
public class TerritoryService {

    private static final BigDecimal MINIMUM_CHARGE = new BigDecimal("1.00");
    /** {@code territories.name} kolonunun sınırı (V1). Varsayılan ad buna göre kırpılır. */
    private static final int MAX_TERRITORY_NAME = 60;

    /**
     * <b>Mağaza dairesinin sabit yarıçapı</b> (V38): 100 metre.
     *
     * <p>🔴 Tek yerde durur ve istemciden <b>hiç alınmaz</b>. Serbest yarıçap,
     * fiyatı alana bağlayan eski modelin kalıntısıydı; artık daire üyeliğin
     * hakkı olduğu için herkesinki aynı büyüklükte olmalı.
     */
    public static final int STORE_RADIUS_M = 100;
    /** Harita katmanı renkleri — frontend Core 2.0 paletiyle aynı tutulur. */
    private static final List<String> OWNER_COLORS = List.of(
            "#2A85FF", "#83BF6E", "#8E59FF", "#FF6A55", "#FF9C2B", "#0F78BD", "#6C3EE8", "#4F9E33");

    private final TerritoryRepository territoryRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final PricingService pricingService;
    private final PricingZoneRepository pricingZoneRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditRecorder auditRecorder;
    private final TerritoryProperties properties;
    private final com.waydee.moderation.application.RestrictionService restrictionService;
    private final com.waydee.identity.application.EmailVerificationService emailVerificationService;
    private final com.waydee.billing.application.InvoiceService invoiceService;
    private final com.waydee.identity.application.PlanService planService;
    /** Kategori çözümü (V52) — harita döngüsü JOIN'siz kalsın diye tek okuma. */
    private final com.waydee.territory.infrastructure.StoreCategoryRepository storeCategoryRepository;
    /** 🔒 Kapak sahipliği kapısı (V53) — başkasının medyası kapak yapılamaz. */
    private final com.waydee.social.application.MediaService mediaService;

    // ------------------------------------------------------------ mağaza

    /**
     * <b>Mağaza dairesi kur</b> (V38) — Premium üyeliğin hakkı.
     *
     * <p>🔴 <b>Neden ödeme yok:</b> daire artık km² üzerinden ayrı bir alışveriş
     * değil, <b>üyeliğin içeriğidir</b>. Kullanıcı Premium'a zaten ödedi; ikinci
     * bir tahsilat aynı hakkı iki kez satmak olurdu. Bu yüzden fiyat teklifi
     * (quote), süre seçimi ve bölge ödeme oturumu <b>tamamen kaldırıldı</b>.
     *
     * <p>🔴 <b>Yarıçap sabittir: {@value #STORE_RADIUS_M} m.</b> İstemciden
     * yarıçap alınmaz. Serbest yarıçap, fiyatı alana bağlayan eski modelin
     * kalıntısıydı; sabit yarıçap hem haritayı okunur tutar hem de "büyük daire
     * çizip şehri kapatma" sorununu kökten bitirir.
     *
     * <p>⚠️ Kullanıcı başına <b>tek</b> mağaza. Aksi halde bir Premium hesap
     * haritayı istediği kadar noktayla doldurabilirdi.
     *
     * <p>⚠️ Mağazanın ömrü <b>üyeliğe bağlıdır</b>: bitişi planın bitişidir.
     * Sabit 365 gün verilseydi, üyeliği biten kullanıcının mağazası haritada
     * kalmaya devam ederdi.
     */
    @Transactional
    public TerritoryResponse createStore(UUID ownerId, double lng, double lat,
                                         String rawName,
                                         com.waydee.territory.api.dto.TerritoryDtos.TerritoryStyleRequest style,
                                         UUID categoryId,
                                         String ip) {
        restrictionService.assertAllowed(ownerId, com.waydee.moderation.domain.RestrictedAction.PURCHASE);
        emailVerificationService.assertVerified(ownerId);
        // 🔒 Kapı sunucuda: istemcinin düğmeyi gizlemesi güvenlik değildir.
        planService.assertCanOwnStore(ownerId);

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        if (!territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId).stream()
                .filter(t -> t.getStatus() != TerritoryStatus.REVOKED)
                .toList().isEmpty()) {
            throw ApiException.badRequest("Zaten bir mağazan var. Yeni yer için mevcut mağazanı taşı.");
        }

        ResolvedRegion region = pricingService.resolve(lng, lat)
                .orElseThrow(() -> new ApiException(ErrorCode.REGION_NOT_AVAILABLE,
                        "Bu bölge henüz mağazalara açık değil"));

        // Eşzamanlı kurulumları sıraya sokar; kontrol ile insert arasını yarışsız kılar.
        territoryRepository.acquirePurchaseLock();
        String wkt = GeoUtils.circle(lng, lat, STORE_RADIUS_M).toText();
        assertAreaFree(wkt);

        String name = rawName != null && !rawName.isBlank()
                ? rawName.trim()
                : defaultNameFrom(owner.getDisplayName() != null && !owner.getDisplayName().isBlank()
                        ? owner.getDisplayName()
                        : owner.getUsername());

        /* ⚠️ `pricePaid` SIFIR yazılır, null değil: kolon NOT NULL ve geçmiş
           satırlarla aynı tipte kalmalı. Sıfır burada "üyelikle geldi" demektir. */
        Territory territory = territoryRepository.save(new Territory(
                owner, name, lng, lat, STORE_RADIUS_M,
                BigDecimal.ZERO, region.currency(),
                region.countryId(), region.provinceId(), region.districtId(), region.pricingZoneId()));
        /* 🔴 Ücretsiz deneme (18 Ağu 2026): planın hakkı yoksa mağaza sabit
           30 günlük deneme olarak açılır ve hak ORADA TÜKETİLİR. Damgayı
           kirayı hesapladıktan SONRA basmak şart — `storeLeaseDays` "hakkı
           duruyor mu" diye soruyor. */
        boolean trial = !owner.canOwnStore();
        territory.applyLeaseDays(storeLeaseDays(owner));
        if (trial) {
            owner.markFreeStoreTrialUsed();
        }
        applyStyle(territory, style);
        /*
         * 🔴 KATEGORİ TOHUMU (V52): istekte kategori yoksa kullanıcının KAYIT
         * SONRASI verdiği cevaba düşülür. Popup mağaza kurulmadan çok önce
         * çıkıyor ve *"bu bilgiyi mağazada kullanacağız"* denen şey tam olarak
         * bu — cevap orada beklerken mağaza kategorisiz açılsaydı kullanıcı
         * aynı soruyu ikinci kez cevaplamak zorunda kalırdı.
         *
         * ⚠️ Tohum SEÇİLEBİLİRLİK kapısından geçirilmez, geçirilir: kullanıcı
         * cevabını verdikten sonra yönetici o kategoriyi pasife almış olabilir.
         * Pasif bir kategoriyi yeni mağazaya yazmak, kapalı kategoriyi
         * kapatmamak olurdu — o durumda mağaza kategorisiz açılır.
         */
        UUID requestedCategory = categoryId != null ? categoryId : owner.getStoreCategoryId();
        if (requestedCategory != null) {
            storeCategoryRepository.findById(requestedCategory)
                    .filter(com.waydee.territory.domain.StoreCategory::isActive)
                    .ifPresent(c -> territory.setCategoryId(c.getId()));
        }

        auditRecorder.record(ownerId, owner.getUsername(), "STORE_CREATED", "TERRITORY",
                territory.getId().toString(),
                Map.of("region", String.valueOf(region.label()),
                        "radiusM", String.valueOf(STORE_RADIUS_M)), ip);

        eventPublisher.publish(new TerritoryPurchasedEvent(
                territory.getId(), ownerId, owner.getUsername(), toFeature(territory)));

        log.info("Mağaza kuruldu: {} → {} ({} · {} m)",
                owner.getUsername(), name, region.label(), STORE_RADIUS_M);
        return TerritoryResponse.from(territory, region.label(), categoryOf(territory));
    }

    /**
     * Mağazanın kalan günü = üyeliğin kalan günü.
     *
     * <p>⚠️ En az 1 gün verilir: {@code expires_at} kolonu NOT NULL ve geçmiş
     * bir tarih, mağazayı doğduğu anda süresi geçmiş yapardı.
     */
    private static int storeLeaseDays(User owner) {
        /* 🔴 Ücretsiz deneme: planın hakkı yokken mağaza SABİT 30 GÜNdür.
           Bu dal olmadan aşağıdaki `planExpiresAt == null` durumu devreye
           girer ve deneme mağazası doğduğu gün 1 günlük olurdu. */
        if (!owner.canOwnStore()) {
            return UserPlan.FREE_STORE_TRIAL_DAYS;
        }
        Instant until = owner.getPlanExpiresAt();
        if (until == null) {
            return 1;
        }
        long days = java.time.Duration.between(Instant.now(), until).toDays();
        return (int) Math.max(1, days);
    }

    /**
     * <b>Üyelik uzayınca mağaza da uzar.</b>
     *
     * <p>🔴 Bu olmadan mağaza ilk kurulduğu andaki bitişte kalır ve üyeliğini
     * yenileyen kullanıcının mağazası haritadan düşerdi.
     */
    @Transactional
    public void syncStoreLeaseWithPlan(UUID ownerId, Instant planExpiresAt) {
        if (planExpiresAt == null) {
            return;
        }
        territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId).stream()
                .filter(t -> t.getStatus() != TerritoryStatus.REVOKED)
                .forEach(t -> t.extendUntil(planExpiresAt));
    }

    // ------------------------------------------------------------ purchase

    /**
     * @deprecated V38'de kullanıcı yolundan kaldırıldı — daire artık ödeme değil
     *         üyelik hakkıdır ({@link #createStore}). Yalnız <b>ödemesi çoktan
     *         alınmış</b> eski rezervasyonların webhook'la tamamlanabilmesi için
     *         duruyor; para alınıp bölge verilmemesi kabul edilemez.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public PurchaseIntent prepareCheckout(UUID buyerId, PurchaseRequest request) {
        restrictionService.assertAllowed(buyerId, com.waydee.moderation.domain.RestrictedAction.PURCHASE);
        // Para hareketi doğrulanmış adres ister: faturanın gittiği ve bölge
        // hatırlatmalarının ulaşacağı adresin gerçek olduğu kanıtlanmalı.
        emailVerificationService.assertVerified(buyerId);

        if (request.radiusM() < properties.minRadiusM() || request.radiusM() > properties.maxRadiusM()) {
            throw new ApiException(ErrorCode.RADIUS_OUT_OF_BOUNDS,
                    "Yarıçap %d-%d metre aralığında olmalı".formatted(properties.minRadiusM(), properties.maxRadiusM()));
        }

        ResolvedRegion region = pricingService.resolve(request.lng(), request.lat())
                .orElseThrow(() -> new ApiException(ErrorCode.REGION_NOT_AVAILABLE,
                        "Bu bölge henüz satışa açık değil"));

        String wkt = GeoUtils.circle(request.lng(), request.lat(), request.radiusM()).toText();
        assertAreaFree(wkt);

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        BigDecimal areaKm2 = GeoUtils.circleAreaKm2(request.radiusM());
        /*
         * ⚠️ Tutar SEÇİLEN SÜREYE göre hesaplanır. `calculatePrice` yıllık
         * bedeli verir; kısa süreler onun bir oranıdır (bkz. LeaseDuration).
         * İstemciden gelen gün sayısı burada tanımlı seçeneğe indirgenir —
         * uydurma bir süreyle ucuza uzun kira alınamaz.
         */
        com.waydee.territory.domain.LeaseDuration duration =
                com.waydee.territory.domain.LeaseDuration.ofDays(request.days());
        BigDecimal totalPrice = duration.price(calculatePrice(areaKm2, region.pricePerKm2()));
        String name = request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : defaultNameFrom(region.label());

        return new PurchaseIntent(buyer.getId(), buyer.getEmail(), name, wkt, areaKm2, totalPrice, region,
                duration.days());
    }

    /**
     * Ödemesi <b>alınmış</b> bir satın almayı kalıcılaştırır: bölgeyi oluşturur,
     * faturayı keser ve haritalara yayar.
     *
     * <p>Çakışma kontrolü <b>burada tekrar</b> yapılır: rezervasyon bir güvenlik
     * ağıdır, tek dayanak değil. Kullanıcı ödeme yaparken yönetim aynı yere
     * rezerve alan açmış olabilir.
     */
    @Transactional
    public TerritoryResponse completePaidPurchase(PaidPurchase command) {
        // Eşzamanlı satın almaları sıraya sokar; kontrol ile insert arasını yarışsız kılar.
        territoryRepository.acquirePurchaseLock();
        if (territoryRepository.existsActiveIntersecting(command.wkt())) {
            throw new ApiException(ErrorCode.TERRITORY_OVERLAP,
                    "Seçilen alan başka bir kullanıcının bölgesiyle çakışıyor");
        }
        if (pricingZoneRepository.existsCrossingBoundary(command.wkt())) {
            throw new ApiException(ErrorCode.ZONE_BOUNDARY_CROSSED,
                    "Seçilen alan bir fiyat bölgesinin sınırını kesiyor");
        }

        User buyer = userRepository.findById(command.buyerId())
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        Territory territory = territoryRepository.save(new Territory(
                buyer, command.name(), command.lng(), command.lat(), command.radiusM(),
                command.totalPrice(), command.currency(),
                command.countryId(), command.provinceId(), command.districtId(), command.pricingZoneId()));
        // ⚠️ Süre REZERVASYONDAN gelir; kurucu 365 gün yazar, seçilen süre burada uygulanır.
        territory.applyLeaseDays(com.waydee.territory.domain.LeaseDuration.ofDays(command.leaseDays()).days());
        applyStyle(territory, command.style());

        Purchase purchase = purchaseRepository.save(new Purchase(
                territory.getId(), command.buyerId(), command.totalPrice(), command.currency(),
                command.provider(), command.paymentReference(), command.idempotencyKey()));

        // Fatura AYNI transaction'da kesilir: satın alma geri alınırsa fatura da geri alınır.
        invoiceService.issue(new com.waydee.billing.application.InvoiceService.IssueInvoiceCommand(
                command.buyerId(), territory.getId(), purchase.getId(),
                buyer.getUsername(), buyer.getDisplayName(), buyer.getEmail(),
                command.name(), command.regionLabel(), command.areaKm2(), command.radiusM(),
                command.pricePerKm2(), command.currency(), command.totalPrice(),
                command.provider(), command.paymentReference(),
                "PURCHASE", territory.getLeaseStartedAt(), territory.getExpiresAt(),
                command.couponCode(), command.discountAmount()));

        /*
         * ⚠️ `Map.of` NULL DEĞER KABUL ETMEZ — null gelirse NPE atar ve satın alma
         * 500'e düşer. Bu bir kez üretimde oldu: küresel taban fiyat açıkken idari
         * bölgesi ve fiyat bölgesi olmayan bir noktada `regionLabel` boştu.
         * Etiket artık ResolvedRegion tarafında garanti altına alındı; buradaki
         * `String.valueOf` ikinci savunmadır — bir denetim kaydı hiçbir koşulda
         * para hareketini geri almamalıdır.
         */
        auditRecorder.record(command.buyerId(), buyer.getUsername(), "TERRITORY_PURCHASED", "TERRITORY",
                territory.getId().toString(),
                Map.of("amount", command.totalPrice().toPlainString(),
                        "currency", String.valueOf(command.currency()),
                        "region", String.valueOf(command.regionLabel()),
                        "provider", String.valueOf(command.provider())), command.ip());

        eventPublisher.publish(new TerritoryPurchasedEvent(
                territory.getId(), command.buyerId(), buyer.getUsername(), toFeature(territory)));

        log.info("Alan satın alındı: {} → {} ({} {} · {})",
                buyer.getUsername(), command.name(), command.totalPrice(), command.currency(), command.provider());
        return TerritoryResponse.from(territory, command.regionLabel());
    }

    /**
     * Ad girilmediğinde bölge etiketi varsayılan ad olur — ama iki alanın
     * sınırları aynı değil: {@code territories.name} <b>VARCHAR(60)</b>, etiket ise
     * {@code region_label} (VARCHAR(200)) boyutunda olabilir (fiyat bölgesi adı +
     * idari ad). Kırpılmazsa satın alma <b>ödeme alındıktan sonra</b> patlar ve
     * kayıt FAILED'a düşer; kullanıcı parasını ödemiş ama bölgesi yoktur.
     */
    private static String defaultNameFrom(String label) {
        return label.length() <= MAX_TERRITORY_NAME
                ? label
                : label.substring(0, MAX_TERRITORY_NAME - 1).trim() + "…";
    }

    /** Alan boşta mı — hem mevcut bölgelere hem fiyat bölgesi kenarına bakar. */
    private void assertAreaFree(String wkt) {
        if (territoryRepository.existsActiveIntersecting(wkt)) {
            throw new ApiException(ErrorCode.TERRITORY_OVERLAP,
                    "Seçilen alan başka bir kullanıcının bölgesiyle çakışıyor");
        }
        // Alan bir fiyat bölgesinin kenarını kesemez: ya tamamen içinde, ya tamamen dışında olmalı.
        if (pricingZoneRepository.existsCrossingBoundary(wkt)) {
            throw new ApiException(ErrorCode.ZONE_BOUNDARY_CROSSED,
                    "Seçilen alan bir fiyat bölgesinin sınırını kesiyor");
        }
    }

    /** Doğrulanmış satın alma niyeti — ödeme oturumu bu değerlerle açılır. */
    public record PurchaseIntent(
            UUID buyerId,
            String buyerEmail,
            String name,
            String wkt,
            BigDecimal areaKm2,
            BigDecimal totalPrice,
            ResolvedRegion region,
            /** Seçilen kira süresi (gün) — rezervasyona yazılır. */
            int leaseDays
    ) {
    }

    /** Ödemesi alınmış satın alma; tüm alanlar rezervasyondan gelir, istemciden DEĞİL. */
    public record PaidPurchase(
            UUID buyerId,
            String name,
            double lng,
            double lat,
            int radiusM,
            String wkt,
            BigDecimal areaKm2,
            BigDecimal pricePerKm2,
            BigDecimal totalPrice,
            String currency,
            String regionLabel,
            UUID countryId,
            UUID provinceId,
            UUID districtId,
            UUID pricingZoneId,
            Map<String, Object> style,
            String provider,
            String paymentReference,
            String idempotencyKey,
            String ip,
            /** Uygulanan kupon kodu (yoksa null) — faturaya kopyalanır. */
            String couponCode,
            /** İndirim tutarı (yoksa null). `totalPrice` zaten indirimli tutardır. */
            java.math.BigDecimal discountAmount,
            /** Kira süresi (gün) — rezervasyondan gelir, istemciden DEĞİL. */
            int leaseDays
    ) {
    }

    // ------------------------------------------------------------ kiralama

    /**
     * Kiralamayı uzatır (varsayılan 12 ay).
     *
     * Fiyat, YENİLEME ANINDAKİ güncel km² fiyatından hesaplanır — bölge o
     * arada daha pahalı bir fiyat bölgesine girmişse yeni fiyat geçerlidir.
     * Fiyat çözülemezse (idari katman kaldırılmışsa) ilk alım bedeli taban
     * alınır; böylece yönetim boşluğu kullanıcının bölgesini düşürmez.
     *
     * ⚠️ EXPIRED bölge de yenilenebilir — kirası bitmiş kullanıcı bölgesini
     * geri alabilmelidir. Kilit nokta: yenileme sırasında **çakışma kontrolü
     * yapılmaz**, çünkü bölge zaten yerinde durur (silinmedi, sadece
     * haritadan düştü) ve o alan başkasına satılamaz.
     */
    @Transactional(readOnly = true)
    public RenewalIntent prepareRenewal(UUID territoryId, UUID ownerId) {
        Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        if (!territory.getOwner().getId().equals(ownerId)) {
            throw ApiException.forbidden("Bu alanın sahibi değilsiniz");
        }
        if (territory.getStatus() == TerritoryStatus.REVOKED) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Yönetim tarafından kaldırılan bölge yenilenemez");
        }
        restrictionService.assertAllowed(ownerId, com.waydee.moderation.domain.RestrictedAction.PURCHASE);
        emailVerificationService.assertVerified(ownerId);

        BigDecimal areaKm2 = territory.getAreaKm2();
        Optional<ResolvedRegion> resolved =
                pricingService.resolve(territory.getCenter().getX(), territory.getCenter().getY());
        BigDecimal totalPrice = resolved
                .map(r -> calculatePrice(areaKm2, r.pricePerKm2()))
                .orElse(territory.getPricePaid());
        String currency = resolved.map(ResolvedRegion::currency).orElse(territory.getCurrency());
        String label = resolved.map(ResolvedRegion::label).orElseGet(() -> resolveRegionLabel(territory));

        return new RenewalIntent(territory.getId(), ownerId, territory.getOwner().getEmail(),
                territory.getName(), label, areaKm2,
                resolved.map(ResolvedRegion::pricePerKm2).orElse(null), totalPrice, currency);
    }

    /** Ödemesi alınmış kira uzatması. Çakışma kontrolü YOK — bölge zaten yerinde. */
    @Transactional
    public TerritoryResponse completePaidRenewal(PaidRenewal command) {
        Territory territory = territoryRepository.findWithOwnerById(command.territoryId())
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        User owner = territory.getOwner();

        territory.renew(Territory.DEFAULT_LEASE_MONTHS, Instant.now());

        Purchase purchase = purchaseRepository.save(new Purchase(
                territory.getId(), command.ownerId(), command.totalPrice(), command.currency(),
                command.provider(), command.paymentReference(), null, "RENEWAL"));

        invoiceService.issue(new com.waydee.billing.application.InvoiceService.IssueInvoiceCommand(
                command.ownerId(), territory.getId(), purchase.getId(),
                owner.getUsername(), owner.getDisplayName(), owner.getEmail(),
                territory.getName(), command.regionLabel(), command.areaKm2(), territory.getRadiusM(),
                command.pricePerKm2(), command.currency(), command.totalPrice(),
                command.provider(), command.paymentReference(),
                "RENEWAL", territory.getLeaseStartedAt(), territory.getExpiresAt(),
                command.couponCode(), command.discountAmount()));

        auditRecorder.record(command.ownerId(), owner.getUsername(), "TERRITORY_RENEWED", "TERRITORY",
                territory.getId().toString(),
                Map.of("amount", command.totalPrice().toPlainString(), "currency", command.currency(),
                        "expiresAt", territory.getExpiresAt().toString(), "provider", command.provider()),
                command.ip());

        // Süresi dolmuşken yenilendiyse haritaya geri gelmeli.
        eventPublisher.publish(new TerritoryStyleChangedEvent(territory.getId(), toFeature(territory)));

        log.info("Kiralama yenilendi: {} → {} (yeni bitiş {})",
                owner.getUsername(), territory.getName(), territory.getExpiresAt());
        return TerritoryResponse.from(territory, command.regionLabel());
    }

    public record RenewalIntent(
            UUID territoryId,
            UUID ownerId,
            String ownerEmail,
            String territoryName,
            String regionLabel,
            BigDecimal areaKm2,
            BigDecimal pricePerKm2,
            BigDecimal totalPrice,
            String currency
    ) {
    }

    public record PaidRenewal(
            UUID territoryId,
            UUID ownerId,
            String regionLabel,
            BigDecimal areaKm2,
            BigDecimal pricePerKm2,
            BigDecimal totalPrice,
            String currency,
            String provider,
            String paymentReference,
            String ip,
            String couponCode,
            java.math.BigDecimal discountAmount
    ) {
    }

    /**
     * Süresi dolan bölgeleri EXPIRED'a düşürür ve haritalardan indirir.
     *
     * Zamanlanmış iş çağırır; sonuç sayısı loglanır. Tek tek event yayınlanır
     * çünkü istemci haritaları hangi dairenin düştüğünü bilmek zorundadır.
     */
    @Transactional
    public int expireLapsedLeases() {
        Instant now = Instant.now();
        List<Territory> lapsed = territoryRepository.findLapsed(now, TerritoryStatus.ACTIVE);
        for (Territory territory : lapsed) {
            territory.markExpired();
            eventPublisher.publish(new TerritoryRemovedEvent(territory.getId()));
        }
        if (!lapsed.isEmpty()) {
            log.info("Kirası dolan {} bölge EXPIRED'a düştü", lapsed.size());
        }
        return lapsed.size();
    }

    // ------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<TerritoryResponse> myTerritories(UUID ownerId) {
        return territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId).stream()
                .map(t -> TerritoryResponse.from(t, resolveRegionLabel(t)))
                .toList();
    }

    /** Bir kullanıcının herkese açık (aktif) bölgeleri — kullanıcı profili sayfası için. */
    @Transactional(readOnly = true)
    public List<TerritoryResponse> userTerritories(UUID ownerId) {
        return territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(ownerId).stream()
                .filter(t -> t.getStatus() == TerritoryStatus.ACTIVE)
                .map(t -> TerritoryResponse.from(t, resolveRegionLabel(t)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TerritoryResponse get(UUID id) {
        Territory territory = territoryRepository.findWithOwnerById(id)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        return TerritoryResponse.from(territory, resolveRegionLabel(territory));
    }

    /**
     * Haritanın gördüğü küme: aktif + admin tarafından gizlenmemiş bölgeler.
     *
     * <p>🔴 <b>Önbellekli</b> (ölçek analizi K2/C7) — 30 sn tavan; satın alma,
     * görünüm değişikliği ve kaldırma olaylarında {@code MapCacheEvictor}
     * anında boşaltır.
     */
    @Cacheable(cacheNames = CacheConfig.MAP_TERRITORIES)
    @Transactional(readOnly = true)
    public Map<String, Object> territoriesAsGeoJson() {
        /* ⚠️ Kategori haritası döngüden ÖNCE bir kez kurulur — bkz. categoryIndex(). */
        var categories = categoryIndex();
        List<Map<String, Object>> features = territoryRepository.findAllVisibleWithOwner().stream()
                .map(territory -> toFeature(territory, false, categories))
                .toList();
        return GeoJson.featureCollection(features);
    }

    /**
     * Landing (kimliksiz) haritası. Bölgelerin yeri herkese açıktır; ancak
     * **gizli hesapların** sahip kimliği burada verilmez — profilleri de zaten
     * kimliksiz görüntülenemez.
     */
    @Cacheable(cacheNames = CacheConfig.MAP_TERRITORIES_PUBLIC)
    @Transactional(readOnly = true)
    public Map<String, Object> publicTerritoriesAsGeoJson() {
        var categories = categoryIndex();
        List<Map<String, Object>> features = territoryRepository.findAllVisibleWithOwner().stream()
                .map(territory -> {
                    Map<String, Object> feature = toFeature(territory, false, categories);
                    if (territory.getOwner().isPrivateAccount() && !territory.isReserved()) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> props = (Map<String, Object>) feature.get("properties");
                        props.put("ownerId", "");
                        props.put("ownerUsername", "gizli");
                        props.put("ownerDisplayName", "Gizli hesap");
                        props.remove("ownerAvatarUrl");
                        /* ⚠️ İşaretçi adresi de gitmeli: kalsaydı gizli hesabın
                           fotoğrafı haritada halkanın içinde görünmeye devam
                           ederdi — maskeleme yalnız yazıda kalırdı. */
                        props.remove("avatarStreamUrl");
                        /* ⚠️ Kapak da gitmeli (V53): gizli hesabın kişisel
                           fotoğrafı kimliksiz haritada panelin başlığı olarak
                           durmaya devam ederdi. */
                        props.remove("coverUrl");
                    }
                    return feature;
                })
                .toList();
        return GeoJson.featureCollection(features);
    }

    // ------------------------------------------------------------ görünüm

    /** Bölge adı ve görsel özelleştirmesini günceller (yalnız sahibi). */
    @Transactional
    public TerritoryResponse update(UUID territoryId, UUID ownerId,
                                    com.waydee.territory.api.dto.TerritoryDtos.UpdateTerritoryRequest request) {
        Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        if (!territory.getOwner().getId().equals(ownerId)) {
            throw ApiException.forbidden("Bu alanın sahibi değilsiniz");
        }
        if (request.name() != null && !request.name().isBlank()) {
            territory.setName(request.name().trim());
        }
        applyStyle(territory, request.style());
        applyCategory(territory, request.categoryId());
        applyCover(territory, ownerId, request.coverMediaId(), request.clearCover());
        // Görünüm değişince harita anında tazelensin.
        eventPublisher.publish(new TerritoryStyleChangedEvent(territory.getId(), toFeature(territory)));
        return TerritoryResponse.from(territory, resolveRegionLabel(territory), categoryOf(territory));
    }

    /**
     * Rezervasyonda JSONB olarak saklanan görünümü uygular.
     *
     * <p>Ödeme akışında görünüm, satın alma <b>başlarken</b> yazılır ve webhook
     * geldiğinde DB'den okunur — istemci ikinci kez veri göndermez. Bu yüzden
     * DTO değil serbest bir harita alan bu aşırı yükleme gerekir.
     */
    private void applyStyle(Territory territory, Map<String, Object> style) {
        if (style == null || style.isEmpty()) {
            return;
        }
        applyStyle(territory, new com.waydee.territory.api.dto.TerritoryDtos.TerritoryStyleRequest(
                asString(style.get("strokeColor")),
                asString(style.get("fillColor")),
                asDecimal(style.get("fillOpacity")),
                asDecimal(style.get("strokeWidth")),
                asString(style.get("effect")),
                asString(style.get("markerStyle"))));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** JSONB'den okunan sayı Integer/Double/BigDecimal olabilir — hepsi karşılanır. */
    private static BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return null;
    }

    private void applyStyle(Territory territory,
                            com.waydee.territory.api.dto.TerritoryDtos.TerritoryStyleRequest style) {
        if (style == null) {
            return;
        }
        if (style.strokeColor() != null) {
            territory.setStrokeColor(style.strokeColor());
        }
        if (style.fillColor() != null) {
            territory.setFillColor(style.fillColor());
        }
        if (style.fillOpacity() != null) {
            territory.setFillOpacity(style.fillOpacity());
        }
        if (style.strokeWidth() != null) {
            territory.setStrokeWidth(style.strokeWidth());
        }
        if (style.effect() != null) {
            territory.setEffect(com.waydee.territory.domain.TerritoryEffect.valueOf(style.effect()));
        }
        if (style.markerStyle() != null) {
            /* ⚠️ `parse` kullanılıyor, `valueOf` değil: eski bir istemci ya da
               elle düzenlenmiş bir satır tanımsız bir ad gönderebilir ve o
               durumda isteğin patlaması yerine varsayılana düşmesi doğrudur —
               işaretçi bir süs değil, mağazanın haritadaki varlığıdır. */
            territory.setStoreMarkerStyle(
                    com.waydee.territory.domain.StoreMarkerStyle.parse(style.markerStyle()));
        }
    }

    /**
     * <b>Mağazamı sil</b> (24 Ağu 2026).
     *
     * <p>Kullanıcı talimatı: *"mağazayı sil kısmını mağaza düzenleye ekle"*.
     *
     * <h3>🔴 Satır SİLİNMEZ, REVOKED'a düşer</h3>
     * <p>Yöneticinin {@link #revoke} yolu da böyle. Sebep: bölgeye bağlı
     * geçmiş kayıtlar var (faturalar, gönderiler, görüntülenme ölçümleri) ve
     * gerçek bir {@code DELETE} ya yabancı anahtar hatası verir ya da
     * CASCADE ile <b>mali kayıtları yok eder</b> — şemadaki
     * {@code users} kararının aynısı ({@link com.waydee.identity.domain.User}).
     * Kullanıcı için sonuç aynı: mağaza haritadan ve profilinden kalkar.
     *
     * <h3>🔴 Ücretsiz deneme hakkı GERİ GELMEZ</h3>
     * <p>{@code freeStoreUsedAt} damgasına dokunulmuyor ve bu bilinçli — V49'da
     * yazılan kural: *"mağaza silinse bile hak geri gelmez"*. Aksi hâlde
     * kullanıcı mağazasını silip yeniden açarak deneme süresini sonsuza
     * uzatırdı.
     *
     * <p>⚠️ Yeni mağaza açmanın önü <b>açılır</b>: kurulum kontrolü
     * {@code REVOKED} olmayan bir bölge arıyor. Yani hakkı olan (Premium)
     * kullanıcı mağazasını başka bir yere taşıyabilir — zaten silmenin asıl
     * kullanım amacı bu.
     */
    @Transactional
    public void deleteOwnStore(UUID territoryId, UUID ownerId, String ip) {
        Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        if (!territory.getOwner().getId().equals(ownerId)) {
            throw ApiException.forbidden("Bu alanın sahibi değilsiniz");
        }
        if (territory.getStatus() == TerritoryStatus.REVOKED) {
            /* Zaten silinmiş: ikinci istek hata değil, aynı sonucu verir
               (idempotent). İstemcinin çift tıklaması 404 görmemeli. */
            return;
        }
        territory.setStatus(TerritoryStatus.REVOKED);
        auditRecorder.record(ownerId, territory.getOwner().getUsername(),
                "STORE_DELETED", "TERRITORY", territoryId.toString(), null, ip);
        eventPublisher.publish(new TerritoryRemovedEvent(territoryId));
    }

    // ------------------------------------------------------------ admin

    @Transactional
    public void revoke(UUID territoryId, AuthenticatedUser actor) {
        Territory territory = territoryRepository.findById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        territory.setStatus(TerritoryStatus.REVOKED);
        auditRecorder.record(actor.id(), actor.username(), "TERRITORY_REVOKED", "TERRITORY",
                territoryId.toString(), null, null);
        eventPublisher.publish(new TerritoryRemovedEvent(territoryId));
    }

    /** Yönetim listesi — gizli ve pasif bölgeler dahil. */
    @Transactional(readOnly = true)
    public PageResponse<AdminTerritoryResponse> adminList(String query, int page, int size) {
        // Boş string = "süzme yok" (LIKE '%%' her satırı tutar); null GEÇME (bkz. repository notu).
        String needle = query != null ? query.trim() : "";
        Page<Territory> result = territoryRepository.adminSearch(needle,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.from(result, AdminTerritoryResponse::from);
    }

    /** Yönetim haritası — gizli/pasif dahil her bölge, yönetim bayraklarıyla. */
    @Transactional(readOnly = true)
    public Map<String, Object> adminTerritoriesAsGeoJson() {
        List<Map<String, Object>> features = territoryRepository.findAllActiveWithOwner().stream()
                .map(t -> {
                    Map<String, Object> feature = toFeature(t, true);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> props = (Map<String, Object>) feature.get("properties");
                    props.put("hidden", t.isHidden());
                    props.put("reserved", t.isReserved());
                    props.put("status", t.getStatus().name());
                    return feature;
                })
                .toList();
        return GeoJson.featureCollection(features);
    }

    /**
     * Admin bölge güncellemesi: ad, sahiplik devri, görünürlük, rezerve durumu,
     * pasife alma/geri getirme ve görünüm özelleştirmesi. Yalnız verilen alanlar uygulanır.
     */
    @Transactional
    public AdminTerritoryResponse adminUpdate(UUID territoryId, AdminUpdateTerritoryRequest request,
                                              AuthenticatedUser actor) {
        Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));

        Map<String, Object> changes = new HashMap<>();
        if (request.name() != null && !request.name().isBlank()) {
            territory.setName(request.name().trim());
            changes.put("name", territory.getName());
        }
        if (request.ownerId() != null && !request.ownerId().equals(territory.getOwner().getId())) {
            User newOwner = userRepository.findById(request.ownerId())
                    .orElseThrow(() -> ApiException.notFound("Devredilecek kullanıcı bulunamadı"));
            territory.reassignOwner(newOwner);
            changes.put("ownerId", newOwner.getId().toString());
        }
        if (request.hidden() != null) {
            territory.setHidden(request.hidden());
            changes.put("hidden", request.hidden());
        }
        if (request.reserved() != null) {
            territory.setReserved(request.reserved());
            changes.put("reserved", request.reserved());
        }
        if (request.reservedLabel() != null) {
            territory.setReservedLabel(request.reservedLabel().isBlank() ? null : request.reservedLabel().trim());
        }
        if (request.status() != null) {
            territory.setStatus(TerritoryStatus.valueOf(request.status()));
            changes.put("status", request.status());
        }
        if (request.verified() != null) {
            territory.setVerified(request.verified());
            changes.put("verified", request.verified());
        }
        if (request.extendLeaseMonths() != null) {
            // Yönetim jesti: ödeme alınmaz, fatura kesilmez — yalnız dönem uzar.
            territory.renew(request.extendLeaseMonths(), Instant.now());
            changes.put("expiresAt", territory.getExpiresAt().toString());
        }
        applyStyle(territory, request.style());

        auditRecorder.record(actor.id(), actor.username(), "TERRITORY_ADMIN_UPDATED", "TERRITORY",
                territoryId.toString(), changes, null);

        // Haritalar anında uysun: görünmez olduysa sil, aksi halde feature'ı tazele.
        if (territory.isHidden() || territory.getStatus() != TerritoryStatus.ACTIVE) {
            eventPublisher.publish(new TerritoryRemovedEvent(territoryId));
        } else {
            eventPublisher.publish(new TerritoryStyleChangedEvent(territoryId, toFeature(territory)));
        }
        return AdminTerritoryResponse.from(territory);
    }

    /**
     * Rezerve alan oluşturur: haritada kurumsal bir bölge belirir, sahip kimliği
     * dışarı verilmez. Çakışma ve fiyat bölgesi kuralları satın almadaki gibi geçerlidir
     * (ödeme yoktur; fiyat 0 yazılır).
     */
    @Transactional
    public AdminTerritoryResponse adminReserve(AdminReserveRequest request, AuthenticatedUser actor) {
        territoryRepository.acquirePurchaseLock();

        if (request.radiusM() < properties.minRadiusM() || request.radiusM() > properties.maxRadiusM()) {
            throw new ApiException(ErrorCode.RADIUS_OUT_OF_BOUNDS,
                    "Yarıçap %d-%d metre aralığında olmalı".formatted(properties.minRadiusM(), properties.maxRadiusM()));
        }
        String wkt = GeoUtils.circle(request.lng(), request.lat(), request.radiusM()).toText();
        if (territoryRepository.existsActiveIntersecting(wkt)) {
            throw new ApiException(ErrorCode.TERRITORY_OVERLAP,
                    "Seçilen alan mevcut bir bölgeyle çakışıyor");
        }
        if (pricingZoneRepository.existsCrossingBoundary(wkt)) {
            throw new ApiException(ErrorCode.ZONE_BOUNDARY_CROSSED,
                    "Seçilen alan bir fiyat bölgesinin sınırını kesiyor");
        }

        User admin = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Yönetici bulunamadı"));
        // Bölge/fiyat çözümlemesi opsiyonel: tanımlı değilse rezerve yine oluşur.
        Optional<ResolvedRegion> region = pricingService.resolve(request.lng(), request.lat());

        Territory territory = territoryRepository.save(new Territory(
                admin, request.name().trim(), request.lng(), request.lat(), request.radiusM(),
                BigDecimal.ZERO, region.map(ResolvedRegion::currency).orElse("TRY"),
                region.map(ResolvedRegion::countryId).orElse(null),
                region.map(ResolvedRegion::provinceId).orElse(null),
                region.map(ResolvedRegion::districtId).orElse(null),
                region.map(ResolvedRegion::pricingZoneId).orElse(null)));
        territory.setReserved(true);
        territory.setReservedLabel(request.reservedLabel() != null && !request.reservedLabel().isBlank()
                ? request.reservedLabel().trim() : null);
        applyStyle(territory, request.style());

        auditRecorder.record(actor.id(), actor.username(), "TERRITORY_RESERVED", "TERRITORY",
                territory.getId().toString(),
                Map.of("name", territory.getName(), "radiusM", request.radiusM()), null);

        eventPublisher.publish(new TerritoryStyleChangedEvent(territory.getId(), toFeature(territory)));
        log.info("Rezerve alan oluşturuldu: {} ({} m)", territory.getName(), request.radiusM());
        return AdminTerritoryResponse.from(territory);
    }

    // ------------------------------------------------------------ helpers

    private BigDecimal calculatePrice(BigDecimal areaKm2, BigDecimal pricePerKm2) {
        BigDecimal price = areaKm2.multiply(pricePerKm2).setScale(2, RoundingMode.HALF_UP);
        return price.max(MINIMUM_CHARGE);
    }

    /**
     * Bölgenin idari etiketi ("İlçe, İl" / "İl, Ülke" / "Ülke").
     * Kart servisi de aynı etiketi kullanır — iki yerde ayrı hesaplanmasın.
     */
    /**
     * Bölgenin konum etiketi — <b>asla null dönmez.</b>
     *
     * <p>⚠️ İdari kimlikler boşsa (küresel taban fiyatla alınan bölgelerin
     * TAMAMI böyledir) koordinat etiketine düşülür. Null dönmesi "Bölgelerim",
     * kullanıcı profili ve bölge detayında konumu <b>"—"</b> gösteriyordu:
     * kullanıcı 1.887 TL ödediği dairenin nerede olduğunu göremiyordu.
     */
    public String resolveRegionLabel(Territory territory) {
        String administrative = administrativeLabel(territory);
        return administrative != null
                ? administrative
                : ResolvedRegion.coordinateLabel(territory.getCenter().getX(), territory.getCenter().getY());
    }

    private String administrativeLabel(Territory territory) {
        if (territory.getDistrictId() != null) {
            return districtRepository.findById(territory.getDistrictId())
                    .map(d -> d.getName() + ", " + d.getProvince().getName())
                    .orElse(null);
        }
        if (territory.getProvinceId() != null) {
            return provinceRepository.findById(territory.getProvinceId())
                    .map(p -> p.getName() + ", " + p.getCountry().getName())
                    .orElse(null);
        }
        if (territory.getCountryId() != null) {
            return countryRepository.findById(territory.getCountryId())
                    .map(com.waydee.geo.domain.Country::getName)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Kimlik → kategori haritası (V52).
     *
     * <p>🔴 Harita GeoJSON'u <b>bütün</b> mağazaları dolaşır. Her satırda
     * kategoriyi ayrı ayrı okumak N+1, ilişkiyle çekmek her satıra bir JOIN
     * demekti. Kategori sayısı onlarla ölçülür: liste bir kez okunur ve
     * döngü buradan çözer.
     *
     * <p>⚠️ Pasifler de haritaya girer. Pasif olmak "artık <b>seçilemez</b>"
     * demektir, "<b>görünmez</b>" değil — yöneticinin kapattığı bir kategoriyi
     * zaten seçmiş mağazanın rozeti bir gecede kaybolmamalı.
     */
    private Map<UUID, com.waydee.territory.domain.StoreCategory> categoryIndex() {
        Map<UUID, com.waydee.territory.domain.StoreCategory> index = new HashMap<>();
        for (var category : storeCategoryRepository.findAll()) {
            index.put(category.getId(), category);
        }
        return index;
    }

    private com.waydee.territory.domain.StoreCategory categoryOf(Territory territory) {
        UUID id = territory.getCategoryId();
        return id != null ? storeCategoryRepository.findById(id).orElse(null) : null;
    }

    /**
     * İstenen kategoriyi doğrular ve mağazaya yazar.
     *
     * <p>⚠️ {@code null} → <b>dokunma</b>. Kategori kaldırma yolu bilinçli
     * olarak yok; bkz. {@code UpdateTerritoryRequest.categoryId}.
     *
     * <p>🔒 Kapı sunucuda: <b>pasif</b> bir kategori yazılamaz. İstemcinin
     * listede göstermemesi güvenlik değildir — kimliği bilen biri yine
     * gönderebilirdi.
     */
    private void applyCategory(Territory territory, UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        var category = storeCategoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Kategori bulunamadı"));
        if (!category.isActive()) {
            throw ApiException.badRequest("Bu kategori artık seçilemiyor");
        }
        territory.setCategoryId(category.getId());
    }

    /**
     * Kapak fotoğrafını uygular (V53).
     *
     * <p>🔒 <b>Sahiplik kapısı sunucuda:</b> istemci başka bir kullanıcının
     * medya kimliğini gönderebilir ve gönderirse o fotoğraf bu mağazanın
     * kapağı olurdu (avatar IDOR'unun aynısı). {@code assertOwnedBy} bunu
     * durdurur.
     *
     * <p>⚠️ Kaldırma ayrı bir bayrakla gelir; {@code null} "dokunma"dır.
     */
    private void applyCover(Territory territory, UUID ownerId, UUID coverMediaId, Boolean clearCover) {
        if (Boolean.TRUE.equals(clearCover)) {
            territory.setStoreCoverMediaId(null);
            return;
        }
        if (coverMediaId == null) {
            return;
        }
        mediaService.assertOwnedBy(coverMediaId, ownerId);
        territory.setStoreCoverMediaId(coverMediaId);
    }

    private Map<String, Object> toFeature(Territory territory) {
        return toFeature(territory, false);
    }

    private Map<String, Object> toFeature(Territory territory, boolean adminView) {
        return toFeature(territory, adminView, null);
    }

    /**
     * @param adminView true ise rezerve bölgelerde de gerçek sahip görünür
     *                  (yönetim konsolu). false → rezerve bölgede kimlik maskelenir.
     */
    private Map<String, Object> toFeature(Territory territory, boolean adminView,
                                          Map<UUID, com.waydee.territory.domain.StoreCategory> categoryIndex) {
        User owner = territory.getOwner();
        boolean maskOwner = territory.isReserved() && !adminView;
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", territory.getId().toString());
        properties.put("name", territory.getName());
        properties.put("reserved", territory.isReserved());
        properties.put("verified", territory.isVerified());
        properties.put("likeCount", territory.getLikeCount());
        properties.put("saveCount", territory.getSaveCount());
        if (maskOwner) {
            // Rezerve alan: sahibi belirsizdir; kullanıcı kimliği hiç sızmaz.
            properties.put("ownerId", "");
            properties.put("ownerUsername", "waydee");
            properties.put("ownerDisplayName", territory.getReservedLabel() != null
                    ? territory.getReservedLabel() : "Rezerve alan");
        } else {
            properties.put("ownerId", owner.getId().toString());
            properties.put("ownerUsername", owner.getUsername());
            properties.put("ownerDisplayName", owner.getDisplayName());
            String ownerAvatarUrl = MediaUrls.of(owner.getAvatarMediaId());
            if (ownerAvatarUrl != null) {
                properties.put("ownerAvatarUrl", ownerAvatarUrl);
                /*
                 * 🔴 <b>İŞARETÇİ İÇİN AYRI, AYNI-ORİJİN ADRES</b> (21 Ağu 2026).
                 *
                 * Harita işaretçisi artık halkalı bir profil fotoğrafı ve o
                 * fotoğraf bir <b>tuvale</b> çiziliyor ({@code drawMarker} →
                 * {@code getImageData}). Tuval, başka bir orijinden gelen bir
                 * görsel çizilince <b>kirlenir</b> ve {@code getImageData}
                 * SecurityError fırlatır — yani işaretçi hiç üretilemez.
                 *
                 * ⚠️ Normal adres depoya (S3) <b>302</b> ile yönlendirir ve S3
                 * <b>CORS başlığı döndürmez</b>. Bu tam olarak 3B mağazada
                 * yaşanan hatadır: yerelde MinIO her Origin'i yankıladığı için
                 * görünmüyordu, <b>üretimde</b> fotoğraflar hiç çıkmıyordu.
                 * {@code streamed} aynı orijinden akıtır ve sorunu bitirir.
                 *
                 * ⚠️ {@code ownerAvatarUrl} DEĞİŞTİRİLMEDİ: onu düz
                 * {@code <img>} kullanan yerler (kartlar, akış) okuyor; onlar
                 * CORS istemez ve 302'de kalarak ölçek kazanımını korur.
                 */
                String streamed = MediaUrls.streamed(owner.getAvatarMediaId());
                if (streamed != null) {
                    properties.put("avatarStreamUrl", streamed);
                }
            }
        }
        // Renk: sahibin özelleştirmesi varsa o, yoksa kullanıcıya göre otomatik palet rengi.
        String autoColor = OWNER_COLORS.get(Math.floorMod(owner.getId().hashCode(), OWNER_COLORS.size()));
        String stroke = territory.getStrokeColor() != null ? territory.getStrokeColor() : autoColor;
        String fill = territory.getFillColor() != null ? territory.getFillColor() : stroke;
        properties.put("color", stroke);
        properties.put("fillColor", fill);
        properties.put("fillOpacity", territory.getFillOpacity() != null
                ? territory.getFillOpacity().doubleValue() : 0.38);
        properties.put("strokeWidth", territory.getStrokeWidth() != null
                ? territory.getStrokeWidth().doubleValue() : 2.5);
        properties.put("effect", territory.getEffect() != null ? territory.getEffect().name() : "NONE");
        /*
         * 🔴 İşaretçi tasarımı (V51). Varsayılan BURADA çözülür, harita
         * ifadesinde değil: aynı kural oturumlu harita, vitrin haritası ve
         * mağaza düzenleme önizlemesinde geçerli; Mapbox ifadesine yazılsaydı
         * üç yerde ayrı ayrı durur ve biri sessizce geride kalırdı.
         *
         * ⚠️ Alan HER ZAMAN dolu gider — hiç seçim yapmamış mevcut mağazalar
         * dahil. "Şimdiki daireleri olan kişilere default gönder" isteği
         * budur; satırlara toplu yazmak yerine okumada çözülüyor, böylece
         * "seçti mi seçmedi mi" bilgisi kayıp olmuyor.
         */
        properties.put("markerStyle", (territory.getStoreMarkerStyle() != null
                ? territory.getStoreMarkerStyle()
                : com.waydee.territory.domain.StoreMarkerStyle.DEFAULT).name());
        /*
         * 🔴 KATEGORİ (V52) — haritanın üstündeki şerit bununla süzer.
         *
         * ⚠️ Süzgeç `categoryId` ile yapılır, `categoryCode` ile DEĞİL: kod
         * yöneticinin eklediği kategorilerde serbest bir metindir ve iki
         * kategorinin kodu birbirine benzeyebilir. Kimlik tekildir.
         *
         * ⚠️ `categoryCode`/`categoryName` de gider çünkü işaretçinin rozeti
         * ve kart, kategoriyi çizmek için ikinci bir istek atmamalı. Ad
         * sunucunun Türkçe yedeğidir; istemci önce sözlüğe bakar.
         *
         * ⚠️ Kategorisi olmayan mağazada alanlar HİÇ yazılmaz (boş dize
         * değil): Mapbox ifadesinde `["has", "categoryId"]` ile
         * "kategorisizler" temiz biçimde elenebilsin.
         */
        var category = categoryIndex != null
                ? (territory.getCategoryId() != null ? categoryIndex.get(territory.getCategoryId()) : null)
                : categoryOf(territory);
        if (category != null) {
            properties.put("categoryId", category.getId().toString());
            properties.put("categoryCode", category.getCode());
            properties.put("categoryName", category.getName());
            properties.put("categoryIcon", category.getIcon());
            properties.put("categoryColor", category.getColor());
        }
        /*
         * 🔴 KAPAK (V53) — haritadaki mağazaya tıklayınca açılan panelin
         * üstündeki geniş görsel.
         *
         * ⚠️ Bu adres bir <b>tuvale</b> çizilmiyor, düz {@code <img>} ile
         * gösteriliyor; bu yüzden işaretçinin ihtiyaç duyduğu aynı-orijin
         * {@code avatarStreamUrl} muamelesi GEREKMİYOR. 302'de kalması
         * ölçek kazancını korur (bkz. yukarıdaki avatar notu).
         *
         * ⚠️ Rezerve/gizli mağazalarda da yazılmaz: aşağıdaki maskeleme
         * yalnız kimliği siliyordu, kapak kişisel bir fotoğraf olabilir.
         */
        if (!maskOwner) {
            String coverUrl = MediaUrls.of(territory.getStoreCoverMediaId());
            if (coverUrl != null) {
                properties.put("coverUrl", coverUrl);
            }
        }
        properties.put("centerLng", territory.getCenter().getX());
        properties.put("centerLat", territory.getCenter().getY());
        // Çizim sırasında komşuya değince durabilmek için istemciye yarıçap da verilir.
        properties.put("radiusM", territory.getRadiusM());
        properties.put("purchasedAt", territory.getPurchasedAt().toString());
        // Kiralama: harita kartı kalan süreyi/rozetleri bunlarla çizer.
        properties.put("expiresAt", territory.getExpiresAt().toString());
        properties.put("leaseStartedAt", territory.getLeaseStartedAt().toString());
        properties.put("daysRemaining", territory.daysRemaining(Instant.now()));
        properties.put("areaKm2", territory.getAreaKm2().doubleValue());
        properties.put("pricePaid", territory.getPricePaid().doubleValue());
        properties.put("currency", territory.getCurrency());
        return GeoJson.feature(territory.getId().toString(), GeoJson.polygon(territory.getBoundary()), properties);
    }
}
