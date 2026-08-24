package com.waydee.billing.api.dto;

import com.waydee.billing.domain.DiscountCoupon;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CouponDtos {

    private CouponDtos() {
    }

    /** Yönetici kupon oluşturma/güncelleme isteği. */
    public record CouponRequest(
            @NotBlank(message = "Kupon kodu zorunludur")
            @Pattern(regexp = "^[A-Za-z0-9_-]{3,40}$",
                    message = "Kod 3-40 karakter olmalı; harf, rakam, tire ve alt çizgi içerebilir")
            String code,

            @Size(max = 200) String description,

            /**
             * V40: varsayilan ve tek uretilen tur <b>PLAN</b> (paket hediyesi).
             * PERCENT/FIXED yalniz gecmis kayitlar icin taniniyor.
             */
            @Pattern(regexp = "PERCENT|FIXED|PLAN", message = "Tür PERCENT, FIXED ya da PLAN olmalı")
            String discountType,

            /** PLAN kuponunda hediye edilen paket. */
            @Pattern(regexp = "PRO|PREMIUM", message = "Paket PRO ya da PREMIUM olmalı")
            String grantPlan,

            /** Hediyenin suresi; bos ise aylik. */
            @Pattern(regexp = "MONTHLY|YEARLY", message = "Dönem MONTHLY ya da YEARLY olmalı")
            String grantPeriod,

            @DecimalMin(value = "0.01", message = "Yüzde 0'dan büyük olmalı")
            BigDecimal percentOff,

            @DecimalMin(value = "0.01", message = "Tutar 0'dan büyük olmalı")
            BigDecimal amountOff,

            @Size(min = 3, max = 3) String currency,
            BigDecimal minAmount,
            BigDecimal maxDiscount,

            @Pattern(regexp = "PURCHASE|RENEWAL|BOTH") String appliesTo,
            Integer maxRedemptions,
            Integer maxPerUser,
            Instant startsAt,
            Instant endsAt,
            Boolean active
    ) {
    }

    public record CouponResponse(
            UUID id,
            String code,
            String description,
            String discountType,
            /** PLAN kuponunda hediye edilen paket; indirim kuponunda null. */
            String grantPlan,
            String grantPeriod,
            BigDecimal percentOff,
            BigDecimal amountOff,
            String currency,
            BigDecimal minAmount,
            BigDecimal maxDiscount,
            String appliesTo,
            Integer maxRedemptions,
            Integer maxPerUser,
            int redemptionCount,
            /** Kalan kullanım hakkı; sınırsızsa null. */
            Integer remaining,
            Instant startsAt,
            Instant endsAt,
            boolean active,
            /** Şu an kullanılabilir mi (tarih + kontenjan + aktiflik birlikte). */
            boolean usable,
            Instant createdAt
    ) {
        public static CouponResponse from(DiscountCoupon c) {
            Instant now = Instant.now();
            boolean inWindow = (c.getStartsAt() == null || !now.isBefore(c.getStartsAt()))
                    && (c.getEndsAt() == null || !now.isAfter(c.getEndsAt()));
            Integer remaining = c.getMaxRedemptions() == null
                    ? null
                    : Math.max(0, c.getMaxRedemptions() - c.getRedemptionCount());
            return new CouponResponse(
                    c.getId(), c.getCode(), c.getDescription(), c.getDiscountType().name(),
                    c.getGrantPlan() == null ? null : c.getGrantPlan().name(),
                    c.getGrantPeriod() == null ? null : c.getGrantPeriod().name(),
                    c.getPercentOff(), c.getAmountOff(), c.getCurrency(),
                    c.getMinAmount(), c.getMaxDiscount(), c.getAppliesTo().name(),
                    c.getMaxRedemptions(), c.getMaxPerUser(), c.getRedemptionCount(), remaining,
                    c.getStartsAt(), c.getEndsAt(), c.isActive(),
                    c.isActive() && inWindow && !c.quotaExhausted(), c.getCreatedAt());
        }
    }

    /** Kullanıcının kod yazarken aldığı bağlayıcı olmayan cevap. */
    public record CouponPreviewRequest(
            @NotBlank(message = "Kupon kodu zorunludur") @Size(max = 40) String code,
            @NotBlank String kind,
            /** Hangi daire için — tutar sunucuda hesaplanır, istemciden alınmaz. */
            Double lng,
            Double lat,
            Integer radiusM,
            /** Kiralama süresi (gün); boşsa varsayılan 365. */
            Integer days,
            UUID territoryId
    ) {
    }

    public record CouponPreviewResponse(
            boolean valid,
            String code,
            String description,
            String reason,
            BigDecimal originalAmount,
            BigDecimal discount,
            BigDecimal finalAmount,
            String currency
    ) {
    }

    // ------------------------------------------------------------ rapor

    public record CouponUsageRow(
            UUID couponId,
            String code,
            long total,
            long confirmed,
            long reserved,
            long released,
            BigDecimal discountGiven,
            BigDecimal revenue,
            long uniqueUsers,
            Instant lastUsedAt
    ) {
    }

    public record CouponRedemptionRow(
            String code,
            String username,
            String status,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            String currency,
            Instant createdAt
    ) {
    }

    public record CouponReport(
            int days,
            long totalRedemptions,
            long confirmedRedemptions,
            BigDecimal totalDiscount,
            BigDecimal revenueAfterDiscount,
            long uniqueUsers,
            int activeCoupons,
            List<CouponUsageRow> byCoupon,
            List<CouponRedemptionRow> recent
    ) {
    }
}
