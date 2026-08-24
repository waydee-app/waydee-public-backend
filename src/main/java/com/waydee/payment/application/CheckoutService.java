package com.waydee.payment.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.geo.GeoUtils;
import com.waydee.identity.domain.BillingPeriod;
import com.waydee.identity.domain.UserPlan;
import com.waydee.payment.domain.CheckoutKind;
import com.waydee.payment.domain.CheckoutStatus;
import com.waydee.payment.domain.PaymentCheckout;
import com.waydee.payment.infrastructure.PaymentCheckoutRepository;
import com.waydee.territory.api.dto.TerritoryDtos.PurchaseRequest;
import com.waydee.territory.application.TerritoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ödeme oturumu + <b>alan rezervasyonu</b> akışı.
 *
 * <p><b>Neden iki adım:</b> gerçek ödemede kullanıcı sağlayıcının sayfasına gider
 * ve sonuç dakikalar sonra webhook ile döner. O boşlukta aynı daire ikinci kez
 * satılmasın diye ödeme başlarken alan rezerve edilir; süresi dolarsa serbest kalır.
 *
 * <p><b>Tek gerçek kaynak DB'dir:</b> webhook geldiğinde bölge, rezervasyonda
 * saklanan değerlerden üretilir. İstemciden ikinci kez veri alınsaydı, ödenen
 * tutardan farklı (daha büyük) bir daire oluşturulabilirdi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final PaymentCheckoutRepository checkoutRepository;
    private final com.waydee.payment.infrastructure.ProcessedOrderRepository processedOrderRepository;
    private final TerritoryService territoryService;
    private final PaymentProviderResolver providers;
    private final PaymentProperties properties;
    private final AuditRecorder auditRecorder;
    private final com.waydee.billing.application.CouponService couponService;
    private final com.waydee.identity.application.PlanService planService;
    private final com.waydee.identity.application.PlanUpgradeService planUpgradeService;
    private final com.waydee.identity.application.PlanPricingService planPricingService;
    private final com.waydee.identity.application.UserService userService;

    // ------------------------------------------------------------ oturum açma

    /* 🔴 `startTerritoryCheckout` ve `startRenewalCheckout` KALDIRILDI (V38).
       Daire km² fiyatıyla satılan bir ürün olmaktan çıkıp PREMIUM üyeliğin
       hakkına dönüştü; kurulum ödemesiz ve tek istekte olur
       (`TerritoryService.createStore`). Rezervasyon/kupon/süre makinesi bu yol
       için gereksizdi — ödeme yoksa rezerve edilecek bir ödeme penceresi de
       yoktur. Tamamlama tarafı yerinde duruyor (aşağıda). */

    /**
     * <b>Üyelik yükseltme oturumu</b> (PRO ya da PREMIUM, aylık ya da yıllık).
     *
     * <p>Akış daire alımıyla birebir aynıdır: oturum açılır, kullanıcı
     * sağlayıcının sayfasında öder, plan ancak <b>imzalı webhook</b> ile
     * yükselir. Ayrı bir yol yazmak ödeme güvenliğini ikinci kez üretmek olurdu.
     *
     * <p>🔴 Tahsil edilen tutar <b>aylık eşdeğerin dönem katıdır</b>: yıllıkta
     * ×12. Fiyat tablosundaki değer aylık eşdeğerdir (bkz. {@code BillingPeriod}).
     */
    @Transactional
    public CheckoutView startPlanCheckout(UUID userId, UserPlan plan, BillingPeriod period,
                                          String email, String ip) {
        if (plan == null || !plan.paid()) {
            throw ApiException.badRequest("Yükseltilecek plan Pro ya da Premium olmalı");
        }
        BillingPeriod term = period == null ? BillingPeriod.MONTHLY : period;

        /* 🔴 "Zaten üyesin" KONTROLÜ YOK (V35): üyelik süreli. Mevcut üyenin bir
           dönem daha satın alması meşru bir eylemdir — `grantPlan()` yeni dönemi
           mevcut bitişin ÜSTÜNE ekler (aynı plansa), kalan gün kaybolmaz. */
        PaymentProvider provider = providers.active();
        Instant expiresAt = Instant.now().plus(properties.reservationTtl());

        /* 🔴 Fiyat ARTIK yapılandırmadan değil, çalışma zamanı ayarından gelir
           (yönetim panelinden değişir). Tutar yine de oturum satırına YAZILIR:
           açık bir ödeme sayfası, fiyat sonradan değişse bile kendi tutarıyla
           tamamlanır. */
        java.math.BigDecimal charge = planPricingService.chargeFor(plan, term);
        String currency = planPricingService.table().currency();
        String label = "Waydee " + (plan == UserPlan.PREMIUM ? "Premium" : "Pro");

        PaymentCheckout checkout = checkoutRepository.save(PaymentCheckout.forPlan(
                userId, provider.name(), CheckoutKind.of(plan), term,
                charge, currency, label, expiresAt, ip));

        return openHostedPage(checkout, resolveEmail(userId, email), label,
                label + " üyelik · " + (term.yearly() ? "yıllık (12 ay)" : "aylık"));
    }

    /**
     * Ödeme sayfasına yazılacak <b>gerçek</b> e-posta.
     *
     * <p>🔴 12 Ağu 2026'da üretimde ölçüldü: uçlar {@code principal.username()}
     * gönderiyordu ve bu projede kullanıcı adı <b>e-posta değildir</b>
     * ({@code "mustafawaydee"}). Polar adresi doğruluyor ve tüm isteği
     * <b>422</b> ile reddediyordu → her yükseltme denemesi 402
     * {@code PAYMENT_FAILED}. LemonSqueezy adresi doğrulamadığı için aynı hata
     * orada <b>sessizce</b> yanlış e-postalı bir ödeme sayfası üretirdi.
     *
     * <p>Kimlik JWT'de e-posta taşımıyor, bu yüzden kullanıcıdan okunur.
     * Okunamazsa {@code null} döner ve <b>alan hiç gönderilmez</b> — Polar
     * kendi sayfasında sorar. Uydurma bir adres göndermek, faturayı yanlış
     * kişiye bağlamak olurdu.
     */
    private String resolveEmail(UUID userId, String fallback) {
        try {
            String email = userService.getMe(userId).email();
            if (isEmail(email)) {
                return email;
            }
        } catch (RuntimeException ex) {
            log.warn("Ödeme için e-posta okunamadı ({}): {}", userId, ex.getMessage());
        }
        return isEmail(fallback) ? fallback : null;
    }

    private static boolean isEmail(String value) {
        return value != null && value.indexOf('@') > 0 && value.indexOf('@') < value.length() - 1;
    }

    private CheckoutView openHostedPage(PaymentCheckout checkout, String email, String title, String description) {
        PaymentProvider provider = providers.active();
        PaymentProvider.HostedCheckout hosted = provider.createCheckout(new PaymentProvider.CheckoutRequest(
                checkout.getId(), checkout.getAmount(), checkout.getCurrency(),
                title, description, email, properties.returnUrl(),
                // Polar tutarı istekten almaz, ÜRÜNDEN alır — ne satıldığını bilmeli.
                checkout.getKind().plan(), checkout.getPlanPeriod()));
        checkout.setCheckoutUrl(hosted.url());
        checkout.setProviderCheckoutId(hosted.providerCheckoutId());
        log.info("Ödeme oturumu açıldı: {} {} ({}) → {}",
                checkout.getAmount(), checkout.getCurrency(), provider.name(), checkout.getId());
        return view(checkout);
    }

    // ------------------------------------------------------------ tamamlama

    /**
     * Sağlayıcı "ödendi" dedi: rezervasyonu tüketip planı/bölgeyi açar.
     *
     * <p><b>Idempotent</b> — aynı sipariş iki kez gelirse (webhook tekrarı,
     * elle yeniden gönderim) ikinci çağrı hiçbir şey yapmaz.
     *
     * <p>🔴 <b>Tekrar mı, yenileme mi?</b> (12 Ağu 2026, Polar'a geçiş) Ayrım
     * artık rezervasyonun durumundan değil <b>sipariş kimliğinden</b> yapılır.
     * Üyelik gerçek bir abonelik olduğu için her dönem yeni bir sipariş gelir ve
     * Polar metadata'yı aboneliğe kopyaladığından <b>aynı rezervasyon
     * kimliğiyle</b> gelir. Eski kontrol (yalnız {@code markPaid}) ikinci ayın
     * ödemesini "tekrar" sanıp yutardı: para alınır, üyelik uzamazdı.
     *
     * <p>Yeni transaction'da koşar: webhook denetleyicisi hata yutmamalı ama
     * bir sipariş için oluşan hata diğerini de geri almamalı.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completePaid(UUID reservationId, String providerOrderId, String ip) {
        if (providerOrderId == null || providerOrderId.isBlank()) {
            throw ApiException.badRequest("Sipariş kimliği olmadan ödeme tamamlanamaz");
        }
        if (processedOrderRepository.existsById(providerOrderId)) {
            log.info("Sipariş zaten işlenmiş, yok sayıldı: {} (rezervasyon {})", providerOrderId, reservationId);
            return;
        }
        PaymentCheckout checkout = checkoutRepository.findById(reservationId)
                .orElseThrow(() -> ApiException.notFound("Ödeme kaydı bulunamadı: " + reservationId));

        /* Defter kaydı ÖNCE ve flush ile yazılır: aynı siparişin iki kopyası
           eşzamanlı gelirse ikincisi birincil anahtar çakışmasına düşer ve o
           transaction geri alınır. Sonradan yazmak, iki thread'in de kontrolü
           geçip planı İKİ dönem uzatmasına izin verirdi. */
        processedOrderRepository.saveAndFlush(
                new com.waydee.payment.domain.ProcessedOrder(providerOrderId, checkout.getId()));

        // Rezervasyon zaten ödenmiş + sipariş yeni ⇒ abonelik yenilemesi.
        if (checkout.getStatus() == CheckoutStatus.PAID) {
            renewPlan(checkout, providerOrderId, ip);
            return;
        }

        if (!checkout.markPaid(providerOrderId)) {
            log.info("Webhook tekrarı yok sayıldı: {} (sipariş {})", reservationId, providerOrderId);
            return;
        }
        // Süresi geçmiş bir bağlantıdan ödeme gelirse alan çoktan başkasına
        // gitmiş olabilir; yine de tamamlamayı DENERİZ — çakışma varsa aşağıda
        // FAILED'a düşer ve iade edilecek kayıt açıkça görünür.
        if (checkout.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Süresi geçmiş rezervasyona ödeme geldi: {}", reservationId);
        }

        try {
            /*
             * Üyelik ödemesinde bölge oluşmaz — kullanıcı planına yükseltilir
             * (V34; V37'de PREMIUM ve yıllık dönem eklendi).
             * ⚠️ Yükseltme burada, ödeme TAMAMLANDIKTAN sonra yapılır: oturum
             * açılırken yapılsaydı ödemeden vazgeçen kullanıcı bedava Pro olurdu.
             */
            if (checkout.getKind().isPlan()) {
                UserPlan granted = checkout.getKind().plan();
                BillingPeriod term = checkout.getPlanPeriod() == null
                        ? BillingPeriod.MONTHLY
                        : checkout.getPlanPeriod();
                planUpgradeService.grant(checkout.getUserId(), granted, term);
                /* 🔴 Mağazanın ömrü üyeliğe bağlı: üyelik uzayınca mağaza da
                   uzamalı. Bu satır olmadan yenileyen kullanıcının mağazası ilk
                   kurulumdaki bitişte kalır ve haritadan düşerdi.
                   ⚠️ Senkron BURADA yapılır (identity'de değil): identity →
                   territory bağımlılığı çevrim yaratırdı, payment ikisini de
                   zaten tanıyor. */
                territoryService.syncStoreLeaseWithPlan(checkout.getUserId(),
                        planService.expiresAt(checkout.getUserId()));
                auditRecorder.record(checkout.getUserId(), null, "PLAN_UPGRADED", "USER",
                        checkout.getUserId().toString(),
                        Map.of("plan", granted.name(), "period", term.name(),
                                "checkout", checkout.getId().toString()), ip);
                return;
            }
            UUID territoryId = checkout.getKind() == CheckoutKind.TERRITORY_PURCHASE
                    ? completePurchase(checkout, ip)
                    : completeRenewal(checkout, ip);
            // Kupon kullanımı ancak ödeme VE bölge tamamlandıktan sonra kesinleşir.
            couponService.confirm(checkout.getId(), territoryId);
        } catch (RuntimeException ex) {
            // ⚠️ Para alındı ama bölge verilemedi: kayıt FAILED olarak durur ve
            // yönetim iade edebilsin diye sebep yazılır. Sessizce yutulmaz.
            checkout.markFailed(ex.getMessage());
            // Kupon yakılmasın — kontenjan geri verilir.
            couponService.release(checkout.getId());
            auditRecorder.record(checkout.getUserId(), null, "PAYMENT_FULFILMENT_FAILED", "PAYMENT_CHECKOUT",
                    checkout.getId().toString(),
                    Map.of("order", String.valueOf(providerOrderId), "reason", String.valueOf(ex.getMessage())), ip);
            log.error("Ödeme alındı ama tamamlanamadı ({}): {}", reservationId, ex.getMessage());
            throw ex;
        }
    }

    /**
     * <b>Abonelik yenilemesi</b> — aynı rezervasyona ikinci (üçüncü…) sipariş.
     *
     * <p>Yeni bir rezervasyon satırı açılmaz: rezervasyonun işi alanı kilitlemek
     * ve ne satıldığını saklamaktı, ikisi de değişmiyor. Yenileme yalnız
     * üyeliğin süresini uzatır ve mağazanın ömrünü ona bağlar.
     *
     * <p>⚠️ Bölge ödemelerinde çağrılmaz: km² ile satılan daire tek seferlikti
     * ve V38'de zaten kaldırıldı. Öyle bir sipariş gelirse <b>işlenmez</b>,
     * yalnız kaydı tutulur — sessizce ikinci bir bölge açmak beklenmedik bir
     * yan etki olurdu.
     */
    private void renewPlan(PaymentCheckout checkout, String providerOrderId, String ip) {
        if (!checkout.getKind().isPlan()) {
            log.warn("Ödenmiş bölge rezervasyonuna ikinci sipariş geldi, yok sayıldı: {} (sipariş {})",
                    checkout.getId(), providerOrderId);
            return;
        }
        UserPlan plan = checkout.getKind().plan();
        BillingPeriod term = checkout.getPlanPeriod() == null
                ? BillingPeriod.MONTHLY
                : checkout.getPlanPeriod();

        planUpgradeService.grant(checkout.getUserId(), plan, term);
        territoryService.syncStoreLeaseWithPlan(checkout.getUserId(),
                planService.expiresAt(checkout.getUserId()));
        auditRecorder.record(checkout.getUserId(), null, "PLAN_RENEWED", "USER",
                checkout.getUserId().toString(),
                Map.of("plan", plan.name(), "period", term.name(),
                        "checkout", checkout.getId().toString(),
                        "order", providerOrderId), ip);
        log.info("Üyelik yenilendi: {} · {} {} (sipariş {})",
                checkout.getUserId(), plan, term, providerOrderId);
    }

    private UUID completePurchase(PaymentCheckout c, String ip) {
        return territoryService.completePaidPurchase(new TerritoryService.PaidPurchase(
                c.getUserId(), c.getTerritoryName(),
                c.getCenter().getX(), c.getCenter().getY(), c.getRadiusM(),
                c.getBoundary().toText(), c.getAreaKm2(), c.getPricePerKm2(), c.getAmount(), c.getCurrency(),
                c.getRegionLabel(), c.getCountryId(), c.getProvinceId(), c.getDistrictId(), c.getPricingZoneId(),
                c.getStyle(), c.getProvider(), c.getProviderOrderId(),
                // Sipariş kimliği aynı zamanda idempotency anahtarıdır.
                c.getProvider() + ":" + c.getProviderOrderId(), ip,
                c.getCouponCode(), c.getDiscountAmount(), c.getLeaseDays())).id();
    }

    private UUID completeRenewal(PaymentCheckout c, String ip) {
        territoryService.completePaidRenewal(new TerritoryService.PaidRenewal(
                c.getTerritoryId(), c.getUserId(), c.getRegionLabel(), c.getAreaKm2(), c.getPricePerKm2(),
                c.getAmount(), c.getCurrency(), c.getProvider(), c.getProviderOrderId(), ip,
                c.getCouponCode(), c.getDiscountAmount()));
        return c.getTerritoryId();
    }

    // ------------------------------------------------------------ sorgu / iptal

    @Transactional(readOnly = true)
    public CheckoutView status(UUID checkoutId, UUID userId) {
        return view(checkoutRepository.findByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> ApiException.notFound("Ödeme kaydı bulunamadı")));
    }

    @Transactional(readOnly = true)
    public List<CheckoutView> myCheckouts(UUID userId) {
        return checkoutRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(CheckoutService::view).toList();
    }

    /** Kullanıcı vazgeçti: rezervasyon hemen serbest bırakılır. */
    @Transactional
    public void cancel(UUID checkoutId, UUID userId) {
        PaymentCheckout checkout = checkoutRepository.findByIdAndUserId(checkoutId, userId)
                .orElseThrow(() -> ApiException.notFound("Ödeme kaydı bulunamadı"));
        checkout.markCancelled();
        // Vazgeçen kullanıcı kuponunu yakmasın.
        couponService.release(checkoutId);
    }

    /**
     * Süresi dolan rezervasyonları serbest bırakır.
     *
     * <p>Sık koşar (dakikada bir): alanın gereksiz yere kilitli kaldığı her
     * dakika, başka bir kullanıcının satın alamadığı bir dakikadır.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void expireStaleReservations() {
        List<PaymentCheckout> stale =
                checkoutRepository.findByStatusAndExpiresAtBefore(CheckoutStatus.PENDING, Instant.now());
        for (PaymentCheckout checkout : stale) {
            checkout.markExpired();
            // Alanla birlikte kupon kontenjanı da geri verilir.
            couponService.release(checkout.getId());
        }
        if (!stale.isEmpty()) {
            log.info("Süresi dolan {} ödeme rezervasyonu serbest bırakıldı", stale.size());
        }
    }

    /** Tamamlanmamış eski kayıtlar tabloyu şişirmesin. */
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeOldCheckouts() {
        int removed = checkoutRepository.deleteStaleBefore(Instant.now().minus(Duration.ofDays(30)));
        if (removed > 0) {
            log.info("{} eski ödeme kaydı temizlendi", removed);
        }
    }

    // ------------------------------------------------------------ yardımcılar

    private static Map<String, Object> styleOf(PurchaseRequest request) {
        if (request.style() == null) {
            return null;
        }
        Map<String, Object> style = new LinkedHashMap<>();
        putIfPresent(style, "strokeColor", request.style().strokeColor());
        putIfPresent(style, "fillColor", request.style().fillColor());
        putIfPresent(style, "fillOpacity", request.style().fillOpacity());
        putIfPresent(style, "strokeWidth", request.style().strokeWidth());
        putIfPresent(style, "effect", request.style().effect());
        return style.isEmpty() ? null : style;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static CheckoutView view(PaymentCheckout c) {
        return new CheckoutView(c.getId(), c.getKind().name(), c.getStatus().name(), c.getProvider(),
                c.getCheckoutUrl(), c.getAmount().toPlainString(),
                c.listAmount().toPlainString(), c.getCouponCode(),
                c.getDiscountAmount() == null ? null : c.getDiscountAmount().toPlainString(),
                c.getCurrency(),
                c.getTerritoryName(), c.getRegionLabel(), c.getExpiresAt(), c.getPaidAt(), c.getFailureReason());
    }

    public record CheckoutView(
            UUID id,
            String kind,
            String status,
            String provider,
            String checkoutUrl,
            /** Tahsil edilecek tutar — kupon uygulandıysa İNDİRİMLİ tutardır. */
            String amount,
            /** İndirim öncesi liste fiyatı (kupon yoksa `amount` ile aynı). */
            String originalAmount,
            String couponCode,
            String discountAmount,
            String currency,
            String territoryName,
            String regionLabel,
            Instant expiresAt,
            Instant paidAt,
            String failureReason
    ) {
    }
}
