package com.waydee.monetization.infrastructure;

import com.waydee.monetization.domain.MonetizationRequest;
import com.waydee.monetization.domain.MonetizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonetizationRequestRepository extends JpaRepository<MonetizationRequest, UUID> {

    /**
     * Kullanıcının açık başvurusu. ⚠️ `Optional` döner çünkü kısmi UNIQUE
     * indeks aynı anda en fazla bir açık kayda izin verir.
     */
    Optional<MonetizationRequest> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, List<MonetizationStatus> statuses);

    /** Kullanıcının EN SON başvurusu — sonuçlanmış olsa da ekranda gösterilir. */
    Optional<MonetizationRequest> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<MonetizationRequest> findByStatusOrderByCreatedAtDesc(MonetizationStatus status, Pageable pageable);

    Page<MonetizationRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(MonetizationStatus status);
}
