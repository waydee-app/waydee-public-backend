package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.CreditLedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, UUID> {

    /** Aynı iş anahtarı daha önce işlendi mi (yükleme/iade tekrarını keser). */
    boolean existsByRefKey(String refKey);

    /** Kullanıcının kredi hareketleri — yeniden eskiye. */
    List<CreditLedgerEntry> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
