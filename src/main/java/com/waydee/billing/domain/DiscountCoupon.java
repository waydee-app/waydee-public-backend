package com.waydee.billing.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * İndirim kuponu.
 *
 * <p>İki tür: <b>yüzde</b> (tavanlı olabilir) ve <b>sabit tutar</b>. Kullanım
 * sayacı ({@code redemptionCount}) <b>atomik SQL</b> ile artar — iki kişi aynı
 * anda son kuponu kullanamaz (bkz. {@code CouponRepository.tryReserve}).
 *
 * <p>Sayaç RESERVED + CONFIRMED kullanımları sayar; ödemesi tamamlanmayan
 * oturum serbest bırakılınca <b>geri düşer</b>.
 */
@Entity
@Table(name = "discount_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiscountCoupon extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", length = 200)
    @Setter
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    @Setter
    private DiscountType discountType;

    @Column(name = "percent_off", precision = 5, scale = 2)
    @Setter
    private BigDecimal percentOff;

    @Column(name = "amount_off", precision = 14, scale = 2)
    @Setter
    private BigDecimal amountOff;

    @Column(name = "currency", length = 3)
    @Setter
    private String currency;

    @Column(name = "min_amount", precision = 14, scale = 2)
    @Setter
    private BigDecimal minAmount;

    @Column(name = "max_discount", precision = 14, scale = 2)
    @Setter
    private BigDecimal maxDiscount;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 10)
    @Setter
    private CouponScope appliesTo;

    @Column(name = "max_redemptions")
    @Setter
    private Integer maxRedemptions;

    @Column(name = "max_per_user")
    @Setter
    private Integer maxPerUser;

    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;

    @Column(name = "starts_at")
    @Setter
    private Instant startsAt;

    @Column(name = "ends_at")
    @Setter
    private Instant endsAt;

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active = true;

    /**
     * <b>Hediye edilen paket</b> (V40): PRO | PREMIUM. Indirim kuponlarinda bos.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grant_plan", length = 10)
    @Setter
    private com.waydee.identity.domain.UserPlan grantPlan;

    /** Hediyenin suresi: MONTHLY (30 gun) | YEARLY (365 gun). */
    @Enumerated(EnumType.STRING)
    @Column(name = "grant_period", length = 10)
    @Setter
    private com.waydee.identity.domain.BillingPeriod grantPeriod;

    public DiscountCoupon(String code) {
        this.code = code;
        this.appliesTo = CouponScope.BOTH;
        this.active = true;
    }

    /** Kod her zaman büyük harfe normalize edilir — kullanıcı küçük yazsa da tutar. */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void rename(String newCode) {
        this.code = normalize(newCode);
    }

    /**
     * Kupon şu an kullanılabilir mi (kontenjan HARİÇ — o atomik sorguda kontrol edilir).
     *
     * @return kullanılamıyorsa gerekçe, kullanılabiliyorsa {@code null}
     */
    public String unusableReason(Instant now, String kind, BigDecimal amount) {
        if (!active) {
            return "Bu kupon şu anda kullanımda değil";
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return "Bu kupon henüz başlamadı";
        }
        if (endsAt != null && now.isAfter(endsAt)) {
            return "Bu kuponun süresi dolmuş";
        }
        if (appliesTo != CouponScope.BOTH && !appliesTo.name().equals(kind)) {
            return appliesTo == CouponScope.PURCHASE
                    ? "Bu kupon yalnız yeni bölge kiralamada geçerli"
                    : "Bu kupon yalnız kira yenilemede geçerli";
        }
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return "Bu kupon en az " + minAmount.stripTrailingZeros().toPlainString() + " tutarında geçerli";
        }
        return null;
    }

    /**
     * İndirim tutarını hesaplar.
     *
     * <p>Sonuç asla tutarı aşmaz — aksi halde negatif ödeme çıkardı. Sabit
     * tutarlı kuponda para birimi uyuşmazsa indirim uygulanmaz (₺ kuponu $
     * ödemesinden düşülemez).
     */
    public BigDecimal discountFor(BigDecimal amount, String orderCurrency) {
        BigDecimal discount;
        if (discountType == DiscountType.PERCENT) {
            discount = amount.multiply(percentOff).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (maxDiscount != null && discount.compareTo(maxDiscount) > 0) {
                discount = maxDiscount;
            }
        } else {
            if (currency != null && !currency.equalsIgnoreCase(orderCurrency)) {
                return BigDecimal.ZERO;
            }
            discount = amountOff;
        }
        return discount.min(amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Hediye kuponu su an kullanilabilir mi?
     *
     * <p>UYARI: tutar/kapsam kontrolleri YOK - hediyede odeme yoktur. Yalniz
     * aktiflik ve tarih penceresi bakilir; kontenjan atomik sorguda karara
     * baglanir.
     *
     * @return kullanilamiyorsa gerekce, kullanilabiliyorsa {@code null}
     */
    public String giftUnusableReason(Instant now) {
        if (discountType != DiscountType.PLAN || grantPlan == null) {
            return "Bu kod bir paket kodu degil";
        }
        if (!active) {
            return "Bu kod su anda kullanimda degil";
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return "Bu kod henuz baslamadi";
        }
        if (endsAt != null && now.isAfter(endsAt)) {
            return "Bu kodun suresi dolmus";
        }
        return null;
    }

    /** Yalnız rapor/gösterim için; kontenjan kararı atomik sorguda verilir. */
    public boolean quotaExhausted() {
        return maxRedemptions != null && redemptionCount >= maxRedemptions;
    }
}
