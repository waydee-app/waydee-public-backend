package com.waydee.moderation.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.moderation.domain.UserReport;
import com.waydee.moderation.domain.UserRestriction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ModerationDtos {

    private ModerationDtos() {
    }

    // ------------------------------------------------------------ şikayet
    public record CreateReportRequest(
            @NotNull(message = "Şikayet edilen kullanıcı zorunludur") UUID reportedUserId,
            @NotBlank(message = "Sebep zorunludur") String reason,
            @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir") String description,
            /** Kanıt görseli (önce /media'ya yüklenir). */
            UUID evidenceMediaId
    ) {
    }

    public record ReportResponse(
            UUID id,
            UserSummary reporter,
            UserSummary reportedUser,
            String reason,
            String reasonLabel,
            String description,
            String evidenceUrl,
            String status,
            String resolutionNote,
            Instant handledAt,
            Instant createdAt,
            /** Şikayet edilen kullanıcının toplam şikayet sayısı. */
            long reportsAgainstUser,
            /** Şikayet edilen kullanıcının hesap durumu (ACTIVE/SUSPENDED). */
            String reportedUserStatus
    ) {
        public static ReportResponse from(UserReport r, UserSummary reporter, UserSummary reported,
                                          long totalAgainst, String reportedStatus) {
            return new ReportResponse(
                    r.getId(), reporter, reported,
                    r.getReason().name(), r.getReason().label(),
                    r.getDescription(),
                    MediaUrls.of(r.getEvidenceMediaId()),
                    r.getStatus().name(),
                    r.getResolutionNote(),
                    r.getHandledAt(),
                    r.getCreatedAt(),
                    totalAgainst,
                    reportedStatus);
        }
    }

    public record ResolveReportRequest(
            /** RESOLVED | REJECTED | REVIEWING */
            @NotBlank String status,
            @Size(max = 500) String note
    ) {
    }

    // ------------------------------------------------------------ kısıtlama
    public record RestrictionRequest(
            @NotBlank(message = "Eylem zorunludur") String action,
            @Size(max = 300) String reason,
            /** null = süresiz. */
            Instant expiresAt
    ) {
    }

    public record RestrictionResponse(
            String action,
            String actionLabel,
            String reason,
            Instant createdAt,
            Instant expiresAt
    ) {
        public static RestrictionResponse from(UserRestriction r) {
            return new RestrictionResponse(r.getAction().name(), r.getAction().label(),
                    r.getReason(), r.getCreatedAt(), r.getExpiresAt());
        }
    }

    /** Seçenek listesi (admin arayüzü ve şikayet formu için). */
    public record OptionResponse(String value, String label) {
    }
}
