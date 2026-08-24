package com.waydee.billing.infrastructure;

import com.waydee.billing.domain.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, UUID> {

    Optional<CouponRedemption> findByCheckoutId(UUID checkoutId);

    /**
     * Kullanici bu kodu daha once ONAYLI kullandi mi (V40)?
     *
     * <p>UYARI: yalniz CONFIRMED sayilir - serbest birakilmis bir kayit
     * kullaniciyi kilitlememeli. Ayni kural veritabaninda kismi tekil indeks
     * olarak da duruyor.
     */
    boolean existsByCouponIdAndUserIdAndStatus(
            UUID couponId, UUID userId, com.waydee.billing.domain.RedemptionStatus status);

    /** Kullanıcının bu kupondaki kullanımı (serbest bırakılanlar sayılmaz). */
    @Query("""
            SELECT COUNT(r) FROM CouponRedemption r
            WHERE r.couponId = :couponId AND r.userId = :userId AND r.status <> 'RELEASED'
            """)
    long countUsageByUser(@Param("couponId") UUID couponId, @Param("userId") UUID userId);

    /**
     * Kupon bazında kullanım özeti — rapor ekranının ana sorgusu.
     * Tek GROUP BY; kupon başına ek sorgu yok.
     */
    @Query("""
            SELECT r.couponId,
                   r.couponCode,
                   COUNT(r),
                   SUM(CASE WHEN r.status = 'CONFIRMED' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = 'RESERVED'  THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = 'RELEASED'  THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = 'CONFIRMED' THEN r.discountAmount ELSE 0 END),
                   SUM(CASE WHEN r.status = 'CONFIRMED' THEN r.finalAmount    ELSE 0 END),
                   COUNT(DISTINCT r.userId),
                   MAX(r.createdAt)
            FROM CouponRedemption r
            WHERE r.createdAt > :since
            GROUP BY r.couponId, r.couponCode
            ORDER BY COUNT(r) DESC
            """)
    List<Object[]> usageSummary(@Param("since") Instant since);

    /** Son kullanımlar (rapor alt tablosu). */
    @Query("""
            SELECT r FROM CouponRedemption r
            WHERE r.createdAt > :since
            ORDER BY r.createdAt DESC
            """)
    List<CouponRedemption> recent(@Param("since") Instant since,
                                  org.springframework.data.domain.Pageable pageable);

    /** Dönem toplamları. */
    @Query("""
            SELECT COUNT(r),
                   SUM(CASE WHEN r.status = 'CONFIRMED' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = 'CONFIRMED' THEN r.discountAmount ELSE 0 END),
                   SUM(CASE WHEN r.status = 'CONFIRMED' THEN r.finalAmount    ELSE 0 END),
                   COUNT(DISTINCT r.userId)
            FROM CouponRedemption r WHERE r.createdAt > :since
            """)
    Object[] totals(@Param("since") Instant since);
}
