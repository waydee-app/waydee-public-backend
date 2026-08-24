package com.waydee.social.api.dto;

import com.waydee.social.api.dto.SocialDtos.AuthorSummary;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StoryDtos {

    private StoryDtos() {
    }

    public record CreateStoryRequest(
            @NotNull(message = "Görsel zorunludur") UUID mediaId,
            @Size(max = 200, message = "Açıklama en fazla 200 karakter olabilir") String caption,
            /**
             * Tek bölge (geriye dönük uyumluluk). Yeni istemciler {@link #territoryIds}
             * kullanır; ikisi de doluysa birleştirilir.
             */
            UUID territoryId,
            /** Hikayenin yayınlanacağı bölgeler. Boş → yalnız kullanıcı profilinde. */
            @Size(max = 20, message = "En fazla 20 bölge seçilebilir") List<UUID> territoryIds
    ) {
    }

    public record StoryResponse(
            UUID id,
            String mediaUrl,
            String caption,
            Instant createdAt,
            Instant expiresAt,
            boolean seen,
            boolean mine,
            long viewCount,
            /** Bölgede yayınlandıysa bölge kimliği + adı (rozet olarak gösterilir). */
            UUID territoryId,
            String territoryName
    ) {
    }

    /** Story paylaşırken seçilebilecek bölge (yalnız profil türü STANDARD olanlar). */
    public record StoryTargetResponse(
            UUID territoryId,
            String name,
            String regionLabel
    ) {
    }

    /** Bir yazarın aktif hikayeleri (story şeridindeki bir halka). */
    public record StoryGroupResponse(
            AuthorSummary author,
            List<StoryResponse> stories,
            boolean hasUnseen
    ) {
    }

    /**
     * Hikayeyi gören bir kişi — göz simgesine dokununca açılan liste.
     *
     * <p>🔴 16 Ağu 2026. Göz simgesi bugüne kadar yalnız <b>sayıyı</b>
     * gösteriyordu; "kim baktı" sorusunun hiçbir karşılığı yoktu.
     *
     * <p>⚠️ Liste <b>yalnız hikayenin sahibine</b> döner (sunucuda kapı var).
     * Başkasının hikayesini kimin gördüğü, o kişinin takip listesini dolaylı
     * olarak sızdırırdı.
     */
    public record StoryViewerResponse(
            AuthorSummary viewer,
            Instant viewedAt
    ) {
    }
}
