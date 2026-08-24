package com.waydee.territory.infrastructure;

import com.waydee.territory.domain.Purchase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    Optional<Purchase> findByIdempotencyKey(String idempotencyKey);

    List<Purchase> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant after);

    @Query("SELECT p.currency, COALESCE(SUM(p.amount), 0) FROM Purchase p WHERE p.status = 'COMPLETED' GROUP BY p.currency")
    List<Object[]> sumRevenueByCurrency();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Purchase p WHERE p.status = 'COMPLETED' AND p.createdAt > :after")
    BigDecimal sumRevenueAfter(@Param("after") Instant after);
}
