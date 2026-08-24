package com.waydee.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * İşlenmiş sağlayıcı siparişi — <b>idempotency defteri</b> (V42).
 *
 * <p>🔴 <b>Neden gerekli:</b> tekrar koruması eskiden rezervasyonun durumundaydı
 * ({@code markPaid} ikinci çağrıda {@code false} döner). Bu, ödemenin ömür boyu
 * <b>bir kez</b> olduğu LemonSqueezy akışında doğruydu. Polar'da üyelik gerçek
 * bir <b>abonelik</b>: her dönem yeni bir {@code order.paid} gelir ve Polar
 * metadata'yı aboneliğe kopyaladığı için <b>aynı rezervasyon kimliğiyle</b>
 * gelir. Eski kontrol bunu "webhook tekrarı" sanıp yok sayardı — kullanıcı her
 * ay öder, üyeliği uzamaz, süresi dolunca planı düşerdi.
 *
 * <p>Doğru anahtar rezervasyon değil <b>sipariş kimliğidir</b>: aynı sipariş iki
 * kez işlenmez, farklı sipariş yeni bir dönem demektir.
 *
 * <p>⚠️ Satır, rezervasyonla birlikte silinir ({@code ON DELETE CASCADE}):
 * {@code purgeOldCheckouts} 30 günden eski kayıtları temizler ve yabancı anahtar
 * onu engelleyemez.
 */
@Entity
@Table(name = "payment_provider_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedOrder {

    /** Sağlayıcıdaki sipariş kimliği — birincil anahtar, tekrarı veritabanı reddeder. */
    @Id
    @Column(name = "order_id", length = 160)
    private String orderId;

    @Column(name = "checkout_id", nullable = false)
    private UUID checkoutId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedOrder(String orderId, UUID checkoutId) {
        this.orderId = orderId;
        this.checkoutId = checkoutId;
        this.processedAt = Instant.now();
    }
}
