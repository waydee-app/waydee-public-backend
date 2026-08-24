package com.waydee.territory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Append-only satın alma işlem kaydı — mock ödeme dahi olsa iz bırakır.
 */
@Entity
@Table(name = "purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "territory_id", nullable = false)
    private UUID territoryId;

    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "payment_reference", nullable = false, length = 64)
    private String paymentReference;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /**
     * PURCHASE (ilk alım) | RENEWAL (kiralama yenileme).
     * Ciro raporunda ikisi ayrı okunabilsin diye tutulur.
     */
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Purchase(UUID territoryId, UUID buyerId, BigDecimal amount, String currency,
                    String paymentMethod, String paymentReference, String idempotencyKey) {
        this(territoryId, buyerId, amount, currency, paymentMethod, paymentReference, idempotencyKey, "PURCHASE");
    }

    public Purchase(UUID territoryId, UUID buyerId, BigDecimal amount, String currency,
                    String paymentMethod, String paymentReference, String idempotencyKey, String kind) {
        this.territoryId = territoryId;
        this.buyerId = buyerId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paymentReference = paymentReference;
        this.status = "COMPLETED";
        this.idempotencyKey = idempotencyKey;
        this.kind = kind;
        this.createdAt = Instant.now();
    }
}
