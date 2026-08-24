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
import java.time.Instant;
import java.util.UUID;

/**
 * Bir satın almanın faturası.
 *
 * ⚠️ **Fatura değişmezdir.** Alıcının adı/e-postası, bölge etiketi ve birim fiyat
 * kesildiği andaki hâliyle **kopyalanarak** saklanır: kullanıcı sonradan adını
 * değiştirse ya da bölge silinse bile geçmiş fatura aynı kalır. Bu yüzden burada
 * `User`/`Territory` ilişkisi değil, kimlik + anlık alan kopyaları tutulur.
 */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** İnsan okunur fatura numarası: WD-<yıl>-<6 hane> (benzersiz). */
    @Column(name = "invoice_no", nullable = false, length = 24)
    private String invoiceNo;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Bölge silinirse NULL olur; fatura yine de geçerlidir. */
    @Column(name = "territory_id")
    private UUID territoryId;

    /** Aynı satın alma iki kez faturalanamaz (DB'de UNIQUE). */
    @Column(name = "purchase_id")
    private UUID purchaseId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private InvoiceStatus status;

    // ---- alıcı (kesildiği andaki hâli)
    @Column(name = "buyer_username", nullable = false, length = 30)
    private String buyerUsername;

    @Column(name = "buyer_name", nullable = false, length = 60)
    private String buyerName;

    @Column(name = "buyer_email", nullable = false, length = 255)
    private String buyerEmail;

    // ---- kalem
    @Column(name = "description", nullable = false, length = 160)
    private String description;

    @Column(name = "region_label", length = 200)
    private String regionLabel;

    @Column(name = "area_km2", nullable = false, precision = 14, scale = 6)
    private BigDecimal areaKm2;

    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(name = "price_per_km2", precision = 12, scale = 2)
    private BigDecimal pricePerKm2;

    // ---- tutarlar
    /**
     * Uygulanan kupon — fatura DEĞİŞMEZ olduğu için kod ve indirim tutarı
     * kesildiği andaki hâliyle kopyalanır; kupon sonradan silinse bile
     * muhasebe kaydı bozulmaz.
     */
    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "discount_amount", precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    // ---- ödeme izi
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    // ---- kiralama izi (değişmez kopya: kesildiği andaki dönem saklanır)
    /** PURCHASE (ilk alım) | RENEWAL (yenileme). */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind = "PURCHASE";

    @Column(name = "lease_started_at")
    private Instant leaseStartedAt;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    /**
     * Faturaya kiralama dönemini işler. Kurucudan ayrı tutulur çünkü kurucu
     * zaten 20 argümanlıdır; alan sayısını büyütmek okunurluğu bitirirdi.
     * Yalnız {@code InvoiceService.issue} içinde, kayıt edilmeden önce çağrılır —
     * fatura dışarıdan hâlâ değişmezdir.
     */
    public void applyLease(String kind, Instant leaseStartedAt, Instant leaseExpiresAt) {
        this.kind = kind;
        this.leaseStartedAt = leaseStartedAt;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    @SuppressWarnings("java:S107") // fatura düz veri taşır; alanlar bilinçli olarak açık
    public Invoice(String invoiceNo, UUID userId, UUID territoryId, UUID purchaseId, InvoiceStatus status,
                   String buyerUsername, String buyerName, String buyerEmail,
                   String description, String regionLabel, BigDecimal areaKm2, int radiusM, BigDecimal pricePerKm2,
                   String currency, BigDecimal subtotal, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal total,
                   String paymentMethod, String paymentReference) {
        this.invoiceNo = invoiceNo;
        this.userId = userId;
        this.territoryId = territoryId;
        this.purchaseId = purchaseId;
        this.issuedAt = Instant.now();
        this.status = status;
        this.buyerUsername = buyerUsername;
        this.buyerName = buyerName;
        this.buyerEmail = buyerEmail;
        this.description = description;
        this.regionLabel = regionLabel;
        this.areaKm2 = areaKm2;
        this.radiusM = radiusM;
        this.pricePerKm2 = pricePerKm2;
        this.currency = currency;
        this.subtotal = subtotal;
        this.taxRate = taxRate;
        this.taxAmount = taxAmount;
        this.total = total;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
    }
}
