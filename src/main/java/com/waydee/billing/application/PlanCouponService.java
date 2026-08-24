package com.waydee.billing.application;

import com.waydee.billing.domain.CouponRedemption;
import com.waydee.billing.domain.DiscountCoupon;
import com.waydee.billing.domain.RedemptionStatus;
import com.waydee.billing.infrastructure.CouponRedemptionRepository;
import com.waydee.billing.infrastructure.CouponRepository;
import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.identity.application.PlanUpgradeService;
import com.waydee.identity.domain.BillingPeriod;
import com.waydee.identity.domain.UserPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Paket kodu kullanma</b> (V40).
 *
 * <p>🔴 Kuponlar artık indirim değil <b>hediye</b>: yönetici "PREMIUM · yıllık"
 * içerikli bir kod üretir, kullanıcı kodu girer ve planı <b>anında</b> yükselir.
 * Ödeme akışına hiç uğranmaz.
 *
 * <p>⚠️ <b>Neden ayrı servis:</b> {@link CouponService} indirim akışının
 * (rezerve → onayla → serbest bırak) üç adımlı durum makinesini taşıyor; o
 * makine ödeme asenkron olduğu için vardı. Hediyede ödeme yok — kod girildiği
 * anda iş biter. İki akışı aynı sınıfa doldurmak, olmayan bir ara duruma
 * bakan kod üretirdi.
 *
 * <p>⚠️ Kontenjan <b>atomik</b> düşülür ({@code tryReserve}): iki kişi son
 * kalan hakkı aynı anda kullanamaz. Plan yükseltme başarısız olursa kontenjan
 * geri verilir — kullanılmamış bir kod yanmamalı.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanCouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final PlanUpgradeService planUpgradeService;
    private final AuditRecorder auditRecorder;

    /**
     * Kodu kullanır ve planı yükseltir.
     *
     * @return kullanıcıya verilen plan + dönem
     */
    @Transactional
    public RedeemResult redeem(String rawCode, UUID userId, String ip) {
        String code = DiscountCoupon.normalize(rawCode);
        if (code.isBlank()) {
            throw ApiException.badRequest("Kod boş olamaz");
        }

        DiscountCoupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("Bu kod geçersiz"));

        String reason = coupon.giftUnusableReason(Instant.now());
        if (reason != null) {
            throw ApiException.badRequest(reason);
        }

        /* ⚠️ "Bu kodu zaten kullandın" kontrolü hem BURADA hem veritabanında
           (kısmi tekil indeks). Yalnız burada olsaydı iki eşzamanlı istek
           ikisini birden geçirirdi. */
        if (redemptionRepository.existsByCouponIdAndUserIdAndStatus(
                coupon.getId(), userId, RedemptionStatus.CONFIRMED)) {
            throw ApiException.badRequest("Bu kodu zaten kullandın");
        }

        if (couponRepository.tryReserve(coupon.getId()) == 0) {
            throw ApiException.badRequest("Bu kodun kullanım hakkı dolmuş");
        }

        UserPlan plan = coupon.getGrantPlan();
        BillingPeriod period = coupon.getGrantPeriod() == null
                ? BillingPeriod.MONTHLY
                : coupon.getGrantPeriod();

        try {
            planUpgradeService.grant(userId, plan, period);
        } catch (RuntimeException e) {
            /* ⚠️ Plan verilemediyse kontenjan GERİ VERİLİR: kullanılamamış bir
               kod yanmamalı. */
            couponRepository.releaseReservation(coupon.getId());
            throw e;
        }

        /* Kullanım kaydı — hediyede tutar yoktur, kolonlar NOT NULL olduğu için
           sıfır yazılır (bkz. V40 yorumu). */
        CouponRedemption redemption = new CouponRedemption(
                coupon.getId(), coupon.getCode(), userId, null,
                BigDecimal.ZERO, BigDecimal.ZERO, "TRY");
        redemption.confirm(null);
        redemptionRepository.save(redemption);

        auditRecorder.record(userId, null, "PLAN_COUPON_REDEEMED", "COUPON",
                coupon.getId().toString(),
                Map.of("code", coupon.getCode(), "plan", plan.name(), "period", period.name()), ip);

        log.info("Paket kodu kullanıldı: {} → {} {} ({})", coupon.getCode(), plan, period, userId);
        return new RedeemResult(coupon.getCode(), plan.name(), period.name());
    }

    public record RedeemResult(String code, String plan, String period) {
    }
}
