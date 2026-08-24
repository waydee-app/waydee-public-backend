package com.waydee.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bir kuponun tek bir kullanımı.
 *
 * <p>Tutarlar o günkü hâliyle <b>kopyalanır</b> — kupon sonradan değiştirilse
 * ya da silinse bile geçmiş rapor bozulmaz (fatura değişmezliğiyle aynı ilke).
 */
@Entity
@Table(name = "coupon_redemptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponRedemption {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "checkout_id")
    private UUID checkoutId;

    @Column(name = "territory_id")
    private UUID territoryId;

    @Column(name = "original_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal finalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "coupon_code", nullable = false, length = 40)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private RedemptionStatus status;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CouponRedemption(UUID couponId, String couponCode, UUID userId, UUID checkoutId,
                            BigDecimal originalAmount, BigDecimal discountAmount, String currency) {
        this.couponId = couponId;
        this.couponCode = couponCode;
        this.userId = userId;
        this.checkoutId = checkoutId;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = originalAmount.subtract(discountAmount);
        this.currency = currency;
        this.status = RedemptionStatus.RESERVED;
        this.createdAt = Instant.now();
    }

    /** @return {@code false} ise zaten kesinleşmişti (webhook tekrarı) */
    public boolean confirm(UUID territory) {
        if (status == RedemptionStatus.CONFIRMED) {
            return false;
        }
        this.status = RedemptionStatus.CONFIRMED;
        this.territoryId = territory;
        this.confirmedAt = Instant.now();
        return true;
    }

    /** @return {@code false} ise serbest bırakılacak bir şey yoktu */
    public boolean release() {
        if (status != RedemptionStatus.RESERVED) {
            return false;
        }
        this.status = RedemptionStatus.RELEASED;
        this.releasedAt = Instant.now();
        return true;
    }
}
