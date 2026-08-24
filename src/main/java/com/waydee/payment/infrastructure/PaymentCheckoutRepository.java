package com.waydee.payment.infrastructure;

import com.waydee.payment.domain.CheckoutStatus;
import com.waydee.payment.domain.PaymentCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCheckoutRepository extends JpaRepository<PaymentCheckout, UUID> {

    Optional<PaymentCheckout> findByIdAndUserId(UUID id, UUID userId);

    Optional<PaymentCheckout> findByProviderAndProviderCheckoutId(String provider, String providerCheckoutId);

    /**
     * Bekleyen bir rezervasyon bu daireyle çakışıyor mu?
     *
     * <p>Süresi geçmiş rezervasyonlar sayılmaz — süpürme işi gecikse bile
     * kimse boşuna beklemez. Kendi kaydını hariç tutabilmek için
     * {@code excludeId} verilir (webhook tamamlarken kendi rezervasyonu
     * çakışma sayılmamalı).
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM payment_checkouts c
              WHERE c.status = 'PENDING' AND c.expires_at > now()
                AND c.boundary IS NOT NULL
                AND (:excludeId IS NULL OR c.id <> CAST(:excludeId AS uuid))
                AND ST_Intersects(c.boundary, ST_GeomFromText(:wkt, 4326))
            )
            """, nativeQuery = true)
    boolean existsPendingIntersecting(@Param("wkt") String wkt, @Param("excludeId") String excludeId);

    /** Kullanıcının aynı bölge için açık bir yenileme oturumu var mı? */
    @Query("""
            SELECT c FROM PaymentCheckout c
            WHERE c.userId = :userId AND c.territoryId = :territoryId
              AND c.status = 'PENDING' AND c.expiresAt > :now
            """)
    Optional<PaymentCheckout> findPendingRenewal(@Param("userId") UUID userId,
                                                 @Param("territoryId") UUID territoryId,
                                                 @Param("now") Instant now);

    List<PaymentCheckout> findByStatusAndExpiresAtBefore(CheckoutStatus status, Instant before);

    /** Kullanıcının ödeme geçmişi (en yeni önce). */
    List<PaymentCheckout> findTop20ByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Çok eski, tamamlanmamış kayıtları temizler (tablo şişmesin). */
    @Modifying
    @Query("DELETE FROM PaymentCheckout c WHERE c.status <> 'PAID' AND c.createdAt < :before")
    int deleteStaleBefore(@Param("before") Instant before);
}
