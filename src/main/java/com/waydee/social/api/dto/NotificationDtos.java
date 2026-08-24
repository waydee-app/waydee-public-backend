package com.waydee.social.api.dto;

import com.waydee.identity.api.dto.FollowDtos.UserSummary;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            UUID id,
            // FOLLOW | FOLLOW_REQUEST | FOLLOW_ACCEPTED | PROFILE_VIEW | POST_LIKE | POST_SAVE
            String type,
            UserSummary actor,
            UUID territoryId,
            String territoryName,
            /** POST_LIKE / POST_SAVE bildiriminin gonderisi (V39); digerlerinde null. */
            UUID postId,
            boolean read,
            Instant createdAt
    ) {
    }

    public record UnreadCountResponse(long unread) {
    }
}
