package com.waydee.monetization.api.dto;

import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.monetization.domain.MonetizationRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class MonetizationDtos {

    private MonetizationDtos() {
    }

    /**
     * Başvuru gövdesi.
     *
     * <p>⚠️ Alanların hepsi <b>sınırlıdır</b> — sınırsız metin kabul eden bir
     * uç, veritabanını şişirmenin ve log'ları boğmanın en kolay yoludur.
     * Uzunluklar tablo kolonlarıyla birebir aynı; aksi halde JPA kesme yerine
     * 500 verirdi.
     */
    public record CreateRequest(
            @NotBlank(message = "Kitleni kısaca anlat")
            @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir")
            String audienceNote,

            @Size(max = 300, message = "Kanal en fazla 300 karakter olabilir")
            String primaryChannel,

            /* ⚠️ Boş bırakılabilir: boşsa hesabın e-postası kullanılır. */
            @Email(message = "Geçerli bir e-posta gir")
            @Size(max = 255)
            String contactEmail
    ) {
    }

    /** Kullanıcının kendi başvurusu — karar notu dâhil. */
    public record MyRequestResponse(
            UUID id,
            String status,
            String audienceNote,
            String primaryChannel,
            String contactEmail,
            String decisionNote,
            Instant handledAt,
            Instant createdAt,
            /** Yeni başvuru gönderilebilir mi (açık başvuru yoksa true). */
            boolean canApply
    ) {
        public static MyRequestResponse from(MonetizationRequest r) {
            return new MyRequestResponse(
                    r.getId(), r.getStatus().name(), r.getAudienceNote(), r.getPrimaryChannel(),
                    r.getContactEmail(), r.getDecisionNote(), r.getHandledAt(), r.getCreatedAt(),
                    !r.isOpen());
        }

        /** Hiç başvuru yokken — ekran "başvurabilirsin" desin diye boş kabuk. */
        public static MyRequestResponse none() {
            return new MyRequestResponse(null, null, null, null, null, null, null, null, true);
        }
    }

    /**
     * Yönetim listesi satırı.
     *
     * <p>⚠️ Kullanıcının <b>e-postası buraya konur</b> — yönetici başvurana
     * dönebilmeli. Kullanıcı ucundaki DTO'da böyle bir alan yoktur; iki DTO
     * bilinçli olarak ayrıdır.
     */
    public record AdminRequestResponse(
            UUID id,
            UserSummary user,
            String userEmail,
            String plan,
            String status,
            String audienceNote,
            String primaryChannel,
            String contactEmail,
            String decisionNote,
            Instant handledAt,
            Instant createdAt
    ) {
    }

    /** Yöneticinin kararı. */
    public record DecisionRequest(
            @NotBlank(message = "Durum zorunludur") String status,
            @Size(max = 1000, message = "Not en fazla 1000 karakter olabilir") String note
    ) {
    }
}
