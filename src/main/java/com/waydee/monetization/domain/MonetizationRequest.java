package com.waydee.monetization.domain;

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
 * <b>Gelir başvurusu.</b> Kullanıcı "Etkini gelire dönüştür" kartından
 * gönderir, yönetici karara bağlar.
 *
 * <p>⚠️ Durum geçişleri <b>tek yönlüdür</b>: PENDING → REVIEWING → APPROVED |
 * REJECTED. Sonuçlanmış bir başvuru geri açılmaz; kullanıcı yeniden başvurur
 * ve yeni bir kayıt oluşur — böylece karar geçmişi bozulmadan durur.
 */
@Entity
@Table(name = "monetization_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonetizationRequest {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MonetizationStatus status;

    @Column(name = "audience_note", length = 1000)
    private String audienceNote;

    @Column(name = "primary_channel", length = 300)
    private String primaryChannel;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MonetizationRequest(UUID userId, String audienceNote, String primaryChannel, String contactEmail) {
        this.userId = userId;
        this.audienceNote = audienceNote;
        this.primaryChannel = primaryChannel;
        this.contactEmail = contactEmail;
        this.status = MonetizationStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Yönetici incelemeye aldı — kullanıcı "bakılıyor" görsün diye ayrı durum. */
    public void markReviewing(UUID adminId) {
        this.status = MonetizationStatus.REVIEWING;
        this.handledBy = adminId;
        this.updatedAt = Instant.now();
    }

    /**
     * Sonuçlandır. {@code note} kullanıcıya gösterilir — reddin gerekçesi
     * olmadan kullanıcı ne düzelteceğini bilemez.
     */
    public void decide(MonetizationStatus decision, UUID adminId, String note) {
        if (decision != MonetizationStatus.APPROVED && decision != MonetizationStatus.REJECTED) {
            throw new IllegalArgumentException("Karar yalnız APPROVED ya da REJECTED olabilir");
        }
        this.status = decision;
        this.handledBy = adminId;
        this.handledAt = Instant.now();
        this.decisionNote = note;
        this.updatedAt = this.handledAt;
    }

    /** Açık başvuru = kullanıcı yeni bir tane gönderemez. */
    public boolean isOpen() {
        return status == MonetizationStatus.PENDING || status == MonetizationStatus.REVIEWING;
    }
}
