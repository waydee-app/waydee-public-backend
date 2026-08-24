package com.waydee.billing.infrastructure;

import com.waydee.billing.domain.DiscountCoupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<DiscountCoupon, UUID> {

    Optional<DiscountCoupon> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            SELECT c FROM DiscountCoupon c
            WHERE :query = '' OR lower(c.code) LIKE lower(concat('%', :query, '%'))
               OR lower(coalesce(c.description, '')) LIKE lower(concat('%', :query, '%'))
            """)
    Page<DiscountCoupon> search(@Param("query") String query, Pageable pageable);

    /**
     * Kontenjandan <b>atomik</b> düşer.
     *
     * <p>⚠️ Bu sorgu kupon sisteminin kalbidir. Önce {@code SELECT} ile sayacı
     * okuyup sonra artırmak, iki isteğin aynı anda son kuponu almasına izin
     * verirdi (klasik lost update). Koşul ve artış <b>tek ifadede</b> olduğu
     * için veritabanı satırı kilitler ve yalnız biri kazanır.
     *
     * @return 1 → yer ayrıldı · 0 → kontenjan dolu (kupon verilmemeli)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DiscountCoupon c SET c.redemptionCount = c.redemptionCount + 1
            WHERE c.id = :id AND (c.maxRedemptions IS NULL OR c.redemptionCount < c.maxRedemptions)
            """)
    int tryReserve(@Param("id") UUID id);

    /** Ödeme tamamlanmadı → ayrılan yer geri verilir. Sayaç negatife düşmez. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DiscountCoupon c SET c.redemptionCount = c.redemptionCount - 1
            WHERE c.id = :id AND c.redemptionCount > 0
            """)
    int releaseReservation(@Param("id") UUID id);
}
