package com.waydee.billing.application;

import com.waydee.billing.domain.CouponRedemption;
import com.waydee.billing.domain.DiscountCoupon;
import com.waydee.billing.domain.RedemptionStatus;
import com.waydee.billing.infrastructure.CouponRedemptionRepository;
import com.waydee.billing.infrastructure.CouponRepository;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Kupon doğrulama ve kullanım yönetimi.
 *
 * <p><b>Akış:</b> önizleme (bağlayıcı değil) → rezervasyon (ödeme oturumu
 * açılırken, kontenjandan atomik düşer) → onay (ödeme geldi) ya da serbest
 * bırakma (oturum düştü, kontenjan geri verilir).
 *
 * <p>⚠️ Kupon <b>ödeme oturumu açılırken</b> rezerve edilir, ödeme anında değil.
 * Aksi halde iki kişi aynı anda son kuponla oturum açar, ikisi de öder ve
 * biri hüsrana uğrardı.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    /**
     * Kuponu <b>bağlayıcı olmadan</b> değerlendirir (kullanıcı kodu yazarken).
     * Kontenjandan düşmez.
     */
    @Transactional(readOnly = true)
    public CouponPreview preview(String rawCode, UUID userId, BigDecimal amount, String currency, String kind) {
        String code = DiscountCoupon.normalize(rawCode);
        if (code.isBlank()) {
            return CouponPreview.invalid(code, "Kupon kodu boş olamaz");
        }
        Optional<DiscountCoupon> found = couponRepository.findByCode(code);
        if (found.isEmpty()) {
            // Kupon var/yok ayrımı sızdırılmaz; tek mesaj.
            return CouponPreview.invalid(code, "Kupon bulunamadı");
        }
        DiscountCoupon coupon = found.get();

        String reason = coupon.unusableReason(Instant.now(), kind, amount);
        if (reason != null) {
            return CouponPreview.invalid(code, reason);
        }
        if (coupon.quotaExhausted()) {
            return CouponPreview.invalid(code, "Bu kuponun kullanım hakkı doldu");
        }
        if (coupon.getMaxPerUser() != null
                && redemptionRepository.countUsageByUser(coupon.getId(), userId) >= coupon.getMaxPerUser()) {
            return CouponPreview.invalid(code, "Bu kuponu daha fazla kullanamazsın");
        }

        BigDecimal discount = coupon.discountFor(amount, currency);
        if (discount.signum() <= 0) {
            return CouponPreview.invalid(code, "Bu kupon bu ödemeye uygulanamıyor");
        }
        return new CouponPreview(true, code, coupon.getDescription(), null,
                discount, amount.subtract(discount), currency);
    }

    /**
     * Kuponu ödeme oturumuna bağlar ve kontenjandan <b>atomik</b> düşer.
     *
     * @return uygulanan indirim; kupon yoksa/geçersizse boş
     * @throws ApiException kod girilmiş ama geçersizse (kullanıcı bilmeli)
     */
    @Transactional
    public Optional<AppliedCoupon> reserve(String rawCode, UUID userId, UUID checkoutId,
                                           BigDecimal amount, String currency, String kind) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }
        CouponPreview p = preview(rawCode, userId, amount, currency, kind);
        if (!p.valid()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, p.reason());
        }
        DiscountCoupon coupon = couponRepository.findByCode(p.code())
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR, "Kupon bulunamadı"));

        // ⚠️ Kontenjan kararı BURADA verilir; preview'daki kontrol yalnız bilgilendirmedir.
        if (couponRepository.tryReserve(coupon.getId()) == 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bu kuponun kullanım hakkı doldu");
        }

        redemptionRepository.save(new CouponRedemption(
                coupon.getId(), coupon.getCode(), userId, checkoutId, amount, p.discount(), currency));
        log.info("Kupon rezerve edildi: {} · indirim {} {} · kullanıcı {}",
                coupon.getCode(), p.discount(), currency, userId);
        return Optional.of(new AppliedCoupon(coupon.getId(), coupon.getCode(), p.discount(), p.finalAmount()));
    }

    /** Ödeme geldi — kullanım kesinleşir. Idempotent. */
    @Transactional
    public void confirm(UUID checkoutId, UUID territoryId) {
        redemptionRepository.findByCheckoutId(checkoutId).ifPresent(r -> {
            if (r.confirm(territoryId)) {
                log.info("Kupon kullanımı kesinleşti: {} (bölge {})", r.getCouponCode(), territoryId);
            }
        });
    }

    /**
     * Oturum düştü (süre doldu / iptal / tamamlanamadı) — kontenjan geri verilir.
     * Idempotent: yalnız RESERVED durumundaki kayıt serbest bırakılır.
     */
    @Transactional
    public void release(UUID checkoutId) {
        redemptionRepository.findByCheckoutId(checkoutId).ifPresent(r -> {
            if (r.release()) {
                couponRepository.releaseReservation(r.getCouponId());
                log.info("Kupon serbest bırakıldı: {} (oturum {})", r.getCouponCode(), checkoutId);
            }
        });
    }

    /** Kullanıcıya gösterilen doğrulama sonucu. */
    public record CouponPreview(
            boolean valid,
            String code,
            String description,
            String reason,
            BigDecimal discount,
            BigDecimal finalAmount,
            String currency
    ) {
        static CouponPreview invalid(String code, String reason) {
            return new CouponPreview(false, code, null, reason, BigDecimal.ZERO, null, null);
        }
    }

    /** Ödeme oturumuna işlenen indirim. */
    public record AppliedCoupon(UUID couponId, String code, BigDecimal discount, BigDecimal finalAmount) {
    }

    /** Rapor için durum sabitleri dışarıdan da okunabilsin. */
    public static final RedemptionStatus[] STATUSES = RedemptionStatus.values();
}
