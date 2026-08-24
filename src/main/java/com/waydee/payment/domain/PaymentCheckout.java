package com.waydee.payment.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Bir ödeme oturumu = <b>alanın rezervasyonu</b>.
 *
 * <p>Sahte ödeme geçidinde satın alma tek transaction'da bitiyordu. Gerçek
 * ödemede kullanıcı sağlayıcının sayfasına gidip döner; arada dakikalar geçer.
 * O boşlukta aynı daire ikinci kez satılmasın diye ödeme başlarken daire
 * <b>geometrisiyle birlikte</b> burada tutulur ve çakışma kontrolüne dahil edilir.
 *
 * <p>Satın alınacak dairenin tüm bilgisi (merkez, yarıçap, fiyat, bölge kimlikleri)
 * ödeme <b>başlarken</b> yazılır. Webhook geldiğinde istemciden hiçbir veri
 * alınmaz — aksi halde ödenen tutardan farklı bir daire oluşturulabilirdi.
 */
@Entity
@Table(name = "payment_checkouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCheckout extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private CheckoutKind kind;

    /** Üyelik ödemesinin dönemi (V37). Bölge ödemelerinde boş. */
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_period", length = 10)
    private com.waydee.identity.domain.BillingPeriod planPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CheckoutStatus status;

    @Column(name = "provider", nullable = false, length = 24)
    private String provider;

    @Column(name = "provider_checkout_id", length = 120)
    @Setter
    private String providerCheckoutId;

    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;

    @Column(name = "checkout_url", columnDefinition = "text")
    @Setter
    private String checkoutUrl;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "price_per_km2", precision = 14, scale = 2)
    private BigDecimal pricePerKm2;

    @Column(name = "area_km2", precision = 14, scale = 6)
    private BigDecimal areaKm2;

    @Column(name = "center", columnDefinition = "geometry(Point,4326)")
    private Point center;

    @Column(name = "boundary", columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    /**
     * Kiralama süresi (gün).
     *
     * ⚠️ Rezervasyonda saklanır: ödeme sayfasında geçen sürede istemci
     * durumunu kaybederse bölge yanlış süreyle açılırdı. Bölge, webhook
     * geldiğinde BU değerle oluşturulur — istemciden değil.
     */
    @Column(name = "lease_days", nullable = false)
    @lombok.Setter
    private int leaseDays = com.waydee.territory.domain.Territory.DEFAULT_LEASE_DAYS;

    @Column(name = "radius_m")
    private Integer radiusM;

    @Column(name = "territory_name", length = 120)
    private String territoryName;

    @Column(name = "region_label", length = 200)
    private String regionLabel;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "province_id")
    private UUID provinceId;

    @Column(name = "district_id")
    private UUID districtId;

    @Column(name = "pricing_zone_id")
    private UUID pricingZoneId;

    /** Satın alma anında seçilen görünüm (renk/opaklık/efekt); serbest biçimli. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style", columnDefinition = "jsonb")
    private Map<String, Object> style;

    /** Yenilemede dolu: kirası uzatılan bölge. */
    @Column(name = "territory_id")
    private UUID territoryId;

    /**
     * Uygulanan kupon. {@code amount} indirim SONRASI tahsil edilecek tutardır;
     * {@code originalAmount} indirim öncesi liste fiyatıdır. İkisi de saklanır
     * çünkü fatura ve rapor "ne kadar indirim verildi"i bilmek zorundadır.
     */
    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "original_amount", precision = 14, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    /** Yeni daire kiralama oturumu. */
    public static PaymentCheckout forPurchase(UUID userId, String provider, BigDecimal amount, String currency,
                                              BigDecimal pricePerKm2, BigDecimal areaKm2,
                                              Point center, Polygon boundary, int radiusM,
                                              String territoryName, String regionLabel,
                                              UUID countryId, UUID provinceId, UUID districtId, UUID pricingZoneId,
                                              Map<String, Object> style, Instant expiresAt, String ip) {
        PaymentCheckout c = new PaymentCheckout();
        c.userId = userId;
        c.kind = CheckoutKind.TERRITORY_PURCHASE;
        c.status = CheckoutStatus.PENDING;
        c.provider = provider;
        c.amount = amount;
        c.currency = currency;
        c.pricePerKm2 = pricePerKm2;
        c.areaKm2 = areaKm2;
        c.center = center;
        c.boundary = boundary;
        c.radiusM = radiusM;
        c.territoryName = territoryName;
        c.regionLabel = regionLabel;
        c.countryId = countryId;
        c.provinceId = provinceId;
        c.districtId = districtId;
        c.pricingZoneId = pricingZoneId;
        c.style = style;
        c.expiresAt = expiresAt;
        c.createdIp = ip;
        return c;
    }

    /**
     * Kira uzatma oturumu. Geometri YAZILMAZ — bölge zaten yerinde durur,
     * dolayısıyla rezerve edilecek bir alan yoktur (çakışma da doğurmaz).
     */
    /**
     * PRO üyelik ödemesi — geometri yok, yalnız tutar ve sahip.
     *
     * <p>⚠️ {@code territoryName} bilinçli olarak plan adıdır: fatura ve ödeme
     * sayfası bir ürün adı ister ve boş bırakmak sağlayıcıda "isimsiz ürün"
     * gösterirdi.
     */
    /**
     * Üyelik ödemesi (V34; V37'de plan + dönem aldı).
     *
     * <p>⚠️ Dönem satıra <b>yazılır</b>: webhook geldiğinde üyeliğin kaç gün
     * uzatılacağı buradan okunur. Tutardan geriye çıkarmak (×12 mi değil mi)
     * fiyat sonradan değiştiğinde yanlış süre verirdi.
     */
    public static PaymentCheckout forPlan(UUID userId, String provider, CheckoutKind kind,
                                          com.waydee.identity.domain.BillingPeriod period,
                                          BigDecimal amount, String currency, String planLabel,
                                          Instant expiresAt, String ip) {
        PaymentCheckout c = new PaymentCheckout();
        c.userId = userId;
        c.kind = kind;
        c.planPeriod = period;
        c.status = CheckoutStatus.PENDING;
        c.provider = provider;
        c.amount = amount;
        c.currency = currency;
        c.territoryName = planLabel;
        c.expiresAt = expiresAt;
        c.createdIp = ip;
        return c;
    }

    public static PaymentCheckout forRenewal(UUID userId, String provider, UUID territoryId,
                                             BigDecimal amount, String currency, BigDecimal pricePerKm2,
                                             BigDecimal areaKm2, String territoryName, String regionLabel,
                                             Instant expiresAt, String ip) {
        PaymentCheckout c = new PaymentCheckout();
        c.userId = userId;
        c.kind = CheckoutKind.TERRITORY_RENEWAL;
        c.status = CheckoutStatus.PENDING;
        c.provider = provider;
        c.territoryId = territoryId;
        c.amount = amount;
        c.currency = currency;
        c.pricePerKm2 = pricePerKm2;
        c.areaKm2 = areaKm2;
        c.territoryName = territoryName;
        c.regionLabel = regionLabel;
        c.expiresAt = expiresAt;
        c.createdIp = ip;
        return c;
    }

    public boolean isPending() {
        return status == CheckoutStatus.PENDING;
    }

    /**
     * Kuponu işler: tahsil edilecek tutarı indirimli hâle düşürür.
     *
     * <p>⚠️ Sağlayıcıya gönderilecek tutar bundan SONRA okunmalıdır — aksi
     * halde kullanıcıdan indirimsiz tutar tahsil edilirdi.
     */
    public void applyCoupon(UUID coupon, String code, BigDecimal discount) {
        this.originalAmount = this.amount;
        this.couponId = coupon;
        this.couponCode = code;
        this.discountAmount = discount;
        this.amount = this.amount.subtract(discount).max(BigDecimal.ZERO);
    }

    /** İndirim öncesi tutar (kupon yoksa tahsil edilen tutarın kendisi). */
    public BigDecimal listAmount() {
        return originalAmount != null ? originalAmount : amount;
    }

    /**
     * Ödemeyi tamamlanmış işaretler.
     *
     * @return {@code false} ise bu sipariş zaten işlenmişti (webhook tekrarı) —
     * çağıran hiçbir şey yapmamalıdır.
     */
    public boolean markPaid(String orderId) {
        if (status == CheckoutStatus.PAID) {
            return false;
        }
        this.status = CheckoutStatus.PAID;
        this.providerOrderId = orderId;
        this.paidAt = Instant.now();
        this.failureReason = null;
        return true;
    }

    public void markExpired() {
        if (status == CheckoutStatus.PENDING) {
            this.status = CheckoutStatus.EXPIRED;
        }
    }

    public void markCancelled() {
        if (status == CheckoutStatus.PENDING) {
            this.status = CheckoutStatus.CANCELLED;
        }
    }

    public void markFailed(String reason) {
        this.status = CheckoutStatus.FAILED;
        this.failureReason = reason != null && reason.length() > 300 ? reason.substring(0, 300) : reason;
    }
}
