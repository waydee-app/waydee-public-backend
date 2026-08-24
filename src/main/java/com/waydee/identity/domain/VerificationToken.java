package com.waydee.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Tek kullanımlık, süreli doğrulama jetonu (e-posta doğrulama · e-posta değişimi ·
 * şifre sıfırlama).
 *
 * <p>Ham jeton <b>yalnız e-postada</b> bulunur; burada SHA-256 özeti saklanır —
 * {@link RefreshToken} ile aynı desen. Veritabanı sızsa bile jetonlar kullanılamaz.
 */
@Entity
@Table(name = "verification_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationToken {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 20)
    private VerificationPurpose purpose;

    /**
     * Yalnız {@link VerificationPurpose#EMAIL_CHANGE} için dolu: doğrulanana kadar
     * yeni adres {@code users} tablosuna yazılmaz, burada bekler.
     */
    @Column(name = "target_email", length = 255)
    private String targetEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public VerificationToken(UUID userId, String tokenHash, VerificationPurpose purpose,
                             String targetEmail, Instant expiresAt, String createdIp) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.targetEmail = targetEmail;
        this.expiresAt = expiresAt;
        this.createdIp = createdIp;
        this.createdAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }
}
