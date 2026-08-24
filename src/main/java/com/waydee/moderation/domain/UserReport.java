package com.waydee.moderation.domain;

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

/** Kullanıcı şikayeti. Kanıt görseli (ekran görüntüsü) opsiyoneldir. */
@Entity
@Table(name = "user_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReport {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ReportReason reason;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "evidence_media_id")
    private UUID evidenceMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserReport(UUID reporterId, UUID reportedUserId, ReportReason reason,
                      String description, UUID evidenceMediaId) {
        this.reporterId = reporterId;
        this.reportedUserId = reportedUserId;
        this.reason = reason;
        this.description = description;
        this.evidenceMediaId = evidenceMediaId;
        this.status = ReportStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void review(UUID adminId) {
        this.status = ReportStatus.REVIEWING;
        this.handledBy = adminId;
        this.handledAt = Instant.now();
    }

    public void close(ReportStatus status, UUID adminId, String note) {
        this.status = status;
        this.handledBy = adminId;
        this.handledAt = Instant.now();
        this.resolutionNote = note;
    }
}
