package com.waydee.billing.application;

import com.waydee.billing.api.dto.CouponDtos.CouponRedemptionRow;
import com.waydee.billing.api.dto.CouponDtos.CouponReport;
import com.waydee.billing.api.dto.CouponDtos.CouponRequest;
import com.waydee.billing.api.dto.CouponDtos.CouponResponse;
import com.waydee.billing.api.dto.CouponDtos.CouponUsageRow;
import com.waydee.billing.domain.CouponScope;
import com.waydee.billing.domain.DiscountCoupon;
import com.waydee.billing.domain.DiscountType;
import com.waydee.billing.infrastructure.CouponRedemptionRepository;
import com.waydee.billing.infrastructure.CouponRepository;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Yönetici tarafı: kupon CRUD + kullanım analizi. */
@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> list(String query, int page, int size) {
        return PageResponse.from(
                couponRepository.search(query == null ? "" : query.trim(),
                        PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))),
                CouponResponse::from);
    }

    @Transactional(readOnly = true)
    public CouponResponse one(UUID id) {
        return CouponResponse.from(require(id));
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {
        String code = DiscountCoupon.normalize(request.code());
        if (couponRepository.existsByCode(code)) {
            throw new ApiException(ErrorCode.CONFLICT, "Bu kupon kodu zaten var");
        }
        DiscountCoupon coupon = new DiscountCoupon(code);
        apply(coupon, request);
        return CouponResponse.from(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponse update(UUID id, CouponRequest request) {
        DiscountCoupon coupon = require(id);
        String code = DiscountCoupon.normalize(request.code());
        if (!code.equals(coupon.getCode())) {
            if (couponRepository.existsByCode(code)) {
                throw new ApiException(ErrorCode.CONFLICT, "Bu kupon kodu zaten var");
            }
            coupon.rename(code);
        }
        apply(coupon, request);
        return CouponResponse.from(coupon);
    }

    /**
     * Kuponu pasife alır. <b>Silmez</b> — geçmiş kullanım kayıtları kupona
     * bağlıdır ve silinirse rapor geçmişi kopar.
     */
    @Transactional
    public void deactivate(UUID id) {
        require(id).setActive(false);
    }

    private void apply(DiscountCoupon coupon, CouponRequest r) {
        /* 🔴 V40: varsayılan tür artık PLAN — kupon bir paket hediyesidir.
           Eskiden varsayılan PERCENT'ti ve tür gönderilmeyen bir istek sessizce
           uygulanacak yeri olmayan bir indirim kuponu üretiyordu. */
        DiscountType type = r.discountType() == null ? DiscountType.PLAN : DiscountType.valueOf(r.discountType());
        coupon.setDiscountType(type);
        coupon.setDescription(blankToNull(r.description()));

        if (type == DiscountType.PLAN) {
            if (r.grantPlan() == null || r.grantPlan().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Hediye edilecek paket seçilmeli");
            }
            coupon.setGrantPlan(com.waydee.identity.domain.UserPlan.valueOf(r.grantPlan()));
            coupon.setGrantPeriod(com.waydee.identity.domain.BillingPeriod.ofNullable(r.grantPeriod()));
            /* ⚠️ İndirim alanları TEMİZLENİR: veritabanındaki `ck_..._shape`
               kısıtı hediye kuponunda bunların dolu olmasını reddediyor ve
               dolu kalan bir alan "bu kupon hem indirim hem hediye mi?"
               belirsizliği yaratırdı. */
            coupon.setPercentOff(null);
            coupon.setAmountOff(null);
            coupon.setMaxDiscount(null);
            coupon.setCurrency(null);
        } else if (type == DiscountType.PERCENT) {
            if (r.percentOff() == null || r.percentOff().signum() <= 0
                    || r.percentOff().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Yüzde indirim 0 ile 100 arasında olmalı");
            }
            coupon.setPercentOff(r.percentOff());
            coupon.setAmountOff(null);
            coupon.setMaxDiscount(r.maxDiscount());
        } else {
            if (r.amountOff() == null || r.amountOff().signum() <= 0) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "İndirim tutarı 0'dan büyük olmalı");
            }
            coupon.setAmountOff(r.amountOff());
            coupon.setPercentOff(null);
            // Sabit tutarda para birimi ŞART: ₺ kuponu $ ödemesinden düşülemez.
            coupon.setCurrency(r.currency() == null || r.currency().isBlank() ? "TRY" : r.currency().toUpperCase());
            coupon.setMaxDiscount(null);
        }

        if (type != DiscountType.PLAN) {
            coupon.setGrantPlan(null);
            coupon.setGrantPeriod(null);
        }
        coupon.setMinAmount(type == DiscountType.PLAN ? null : r.minAmount());
        coupon.setAppliesTo(r.appliesTo() == null ? CouponScope.BOTH : CouponScope.valueOf(r.appliesTo()));
        coupon.setMaxRedemptions(positiveOrNull(r.maxRedemptions()));
        coupon.setMaxPerUser(positiveOrNull(r.maxPerUser()));
        coupon.setStartsAt(r.startsAt());
        coupon.setEndsAt(r.endsAt());
        if (r.endsAt() != null && r.startsAt() != null && !r.endsAt().isAfter(r.startsAt())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bitiş tarihi başlangıçtan sonra olmalı");
        }
        if (r.active() != null) {
            coupon.setActive(r.active());
        }
    }

    // ------------------------------------------------------------ rapor

    /**
     * Kupon kullanım analizi.
     *
     * <p>Maliyet sabit: iki GROUP BY + bir liste sorgusu. Kupon başına ek
     * sorgu yoktur (N+1 yok).
     */
    @Transactional(readOnly = true)
    public CouponReport report(int days) {
        int window = List.of(7, 14, 30, 90, 365).contains(days) ? days : 30;
        Instant since = Instant.now().minus(Duration.ofDays(window));

        Object[] totals = redemptionRepository.totals(since);
        // Tek satırlık toplam sorgusu bazı sürücülerde Object[][] olarak sarılır.
        Object[] t = (totals != null && totals.length == 1 && totals[0] instanceof Object[] inner) ? inner : totals;

        List<CouponUsageRow> byCoupon = redemptionRepository.usageSummary(since).stream()
                .map(r -> new CouponUsageRow(
                        (UUID) r[0], (String) r[1],
                        num(r[2]).longValue(), num(r[3]).longValue(), num(r[4]).longValue(), num(r[5]).longValue(),
                        dec(r[6]), dec(r[7]), num(r[8]).longValue(), (Instant) r[9]))
                .toList();

        List<com.waydee.billing.domain.CouponRedemption> recentRows =
                redemptionRepository.recent(since, PageRequest.of(0, 50));
        Map<UUID, String> usernames = usernamesOf(recentRows.stream().map(x -> x.getUserId()).distinct().toList());
        List<CouponRedemptionRow> recent = recentRows.stream()
                .map(x -> new CouponRedemptionRow(
                        x.getCouponCode(), usernames.getOrDefault(x.getUserId(), "—"), x.getStatus().name(),
                        x.getOriginalAmount(), x.getDiscountAmount(), x.getFinalAmount(),
                        x.getCurrency(), x.getCreatedAt()))
                .toList();

        int activeCoupons = (int) couponRepository.findAll().stream()
                .filter(c -> c.isActive() && !c.quotaExhausted())
                .count();

        return new CouponReport(window,
                t == null ? 0 : num(t[0]).longValue(),
                t == null ? 0 : num(t[1]).longValue(),
                t == null ? BigDecimal.ZERO : dec(t[2]),
                t == null ? BigDecimal.ZERO : dec(t[3]),
                t == null ? 0 : num(t[4]).longValue(),
                activeCoupons, byCoupon, recent);
    }

    private Map<UUID, String> usernamesOf(List<UUID> ids) {
        Map<UUID, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        userRepository.findAllById(ids).forEach(u -> map.put(u.getId(), u.getUsername()));
        return map;
    }

    private DiscountCoupon require(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Kupon bulunamadı"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer positiveOrNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static Number num(Object value) {
        return value instanceof Number n ? n : 0;
    }

    /** SUM(...) sürücüye göre BigDecimal/Double/null dönebilir — hepsi karşılanır. */
    private static BigDecimal dec(Object value) {
        if (value instanceof BigDecimal b) {
            return b;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
