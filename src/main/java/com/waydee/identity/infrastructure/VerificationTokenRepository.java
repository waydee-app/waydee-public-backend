package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.VerificationPurpose;
import com.waydee.identity.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Aynı amaçtaki eski jetonları geçersizleştirir.
     *
     * <p>Yeni bağlantı istendiğinde öncekiler ölmelidir; aksi halde kullanıcının
     * gelen kutusundaki her eski posta hâlâ çalışan bir anahtar olurdu.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE VerificationToken t SET t.usedAt = :now
            WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL
            """)
    int invalidateOpenTokens(@Param("userId") UUID userId,
                             @Param("purpose") VerificationPurpose purpose,
                             @Param("now") Instant now);

    /** Son N dakikada üretilmiş jeton sayısı — "tekrar gönder" yağmurunu keser. */
    @Query("""
            SELECT COUNT(t) FROM VerificationToken t
            WHERE t.userId = :userId AND t.purpose = :purpose AND t.createdAt > :since
            """)
    long countRecent(@Param("userId") UUID userId,
                     @Param("purpose") VerificationPurpose purpose,
                     @Param("since") Instant since);

    /** Süresi dolmuş kullanılmamış jetonların temizliği (zamanlanmış iş). */
    @Modifying
    @Query("DELETE FROM VerificationToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
