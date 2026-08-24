package com.waydee.social.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView;
import com.waydee.identity.domain.User;
import com.waydee.social.domain.PostComment;
import com.waydee.social.domain.TerritoryProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SocialDtos {

    private SocialDtos() {
    }

    public record AuthorSummary(UUID id, String username, String displayName, String avatarUrl) {
        public static AuthorSummary from(User user) {
            return new AuthorSummary(user.getId(), user.getUsername(), user.getDisplayName(),
                    MediaUrls.of(user.getAvatarMediaId()));
        }
    }

    public record MediaResponse(UUID id, String url, String contentType, long sizeBytes) {
    }

    public record ProfileResponse(
            UUID territoryId,
            String title,
            String description,
            String website,
            int postCount,
            /** STANDARD | WEBSITE | HTML */
            String profileType,
            /** Yalnız HTML türünde dolu — sunucuda temizlenmiş içerik. */
            String customHtml,
            /** Sahibinin sosyal medya bağlantıları bu profilde gösterilsin mi. */
            boolean showSocialLinks,
            /** Yalnız {@code showSocialLinks} açıkken dolu; kapalıysa boş liste. */
            List<SocialLinkView> socialLinks
    ) {
        public static ProfileResponse from(TerritoryProfile profile) {
            return from(profile, List.of());
        }

        public static ProfileResponse from(TerritoryProfile profile, List<SocialLinkView> socialLinks) {
            return new ProfileResponse(
                    profile.getTerritoryId(),
                    profile.getTitle(),
                    profile.getDescription(),
                    profile.getWebsite(),
                    profile.getPostCount(),
                    profile.getProfileType() != null ? profile.getProfileType().name() : "STANDARD",
                    profile.getCustomHtml(),
                    profile.isShowSocialLinks(),
                    profile.isShowSocialLinks() ? socialLinks : List.of());
        }
    }

    public record UpdateProfileRequest(
            @Size(max = 80) String title,
            @Size(max = 500) String description,
            @Size(max = 200) String website,
            @Pattern(regexp = "STANDARD|WEBSITE|HTML", message = "Geçersiz profil türü") String profileType,
            @Size(max = 20_000, message = "HTML en fazla 20000 karakter olabilir") String customHtml,
            Boolean showSocialLinks,
            /** Kartın üstünde gösterilecek öne çıkan görsel (sahiplik doğrulanır). */
            UUID featuredMediaId,
            @Size(max = 300) String liveUrl,
            Boolean liveActive
    ) {
    }

    public record CreatePostRequest(
            @Size(max = 1000, message = "Açıklama en fazla 1000 karakter olabilir") String caption,
            @Size(max = 10, message = "Bir gönderiye en fazla 10 görsel eklenebilir") List<UUID> mediaIds,
            /** STANDARD | POLL | EVENT (null → STANDARD). */
            String kind,
            @Size(max = 6, message = "En fazla 6 seçenek") List<@Size(max = 100) String> pollOptions,
            @Size(max = 140) String eventTitle,
            @Size(max = 140) String eventLocation,
            Instant eventStartsAt
    ) {
    }

    public record PollOptionView(UUID id, String text, int voteCount) {
    }

    public record PollView(List<PollOptionView> options, int totalVotes, UUID myOptionId) {
    }

    public record EventView(String title, String location, Instant startsAt,
                            int goingCount, int interestedCount, String myRsvp) {
    }

    public record PollVoteRequest(@NotNull(message = "Seçenek zorunludur") UUID optionId) {
    }

    public record RsvpRequest(@NotBlank(message = "Durum zorunludur") String status) {
    }

    public record PostResponse(
            UUID id,
            UUID territoryId,
            AuthorSummary author,
            String caption,
            List<String> mediaUrls,
            int likeCount,
            int commentCount,
            boolean likedByMe,
            /**
             * Kaydetme sayisi ve "ben kaydettim mi" (V21 tablosu nihayet
             * kullaniliyor).
             *
             * <p>⚠️ Ikisi de yaniti tasir: istemci kaydetme durumunu ayri bir
             * istekle sormak zorunda kalsaydi, akistaki her karo icin bir istek
             * daha atilirdi.
             */
            int saveCount,
            boolean savedByMe,
            Instant createdAt,
            String kind,
            PollView poll,
            EventView event
    ) {
    }

    public record CreateCommentRequest(
            @NotBlank(message = "Yorum boş olamaz")
            @Size(max = 500, message = "Yorum en fazla 500 karakter olabilir")
            String body
    ) {
    }

    public record CommentResponse(
            UUID id,
            AuthorSummary author,
            String body,
            Instant createdAt,
            /**
             * Bakan kişi bu yorumu silebilir mi (yorumu yazan · gönderi sahibi · admin).
             *
             * <p>⚠️ Karar <b>sunucuda</b> verilir; istemci "benim mi?" diye
             * tahmin etmeye çalışmaz. Silme ucu ayrıca kendi kontrolünü yapar —
             * bu bayrak yalnız düğmeyi göstermek içindir.
             */
            boolean canDelete
    ) {
        public static CommentResponse from(PostComment comment, UUID viewerId, UUID postOwnerId, boolean isAdmin) {
            boolean canDelete = isAdmin
                    || (viewerId != null && (viewerId.equals(comment.getAuthor().getId())
                    || viewerId.equals(postOwnerId)));
            return new CommentResponse(comment.getId(), AuthorSummary.from(comment.getAuthor()),
                    comment.getBody(), comment.getCreatedAt(), canDelete);
        }
    }
}
