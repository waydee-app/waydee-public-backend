package com.waydee.social.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.social.domain.ActivityEvent;

import java.time.Instant;
import java.util.UUID;

public final class ActivityDtos {

    private ActivityDtos() {
    }

    public record ActivityResponse(
            UUID id,
            String type,
            UUID actorId,
            String actorUsername,
            String actorDisplayName,
            String actorAvatarUrl,
            UUID territoryId,
            String territoryName,
            Instant createdAt
    ) {
        public static ActivityResponse from(ActivityEvent e) {
            return new ActivityResponse(
                    e.getId(),
                    e.getType(),
                    e.getActorId(),
                    e.getActorUsername(),
                    e.getActorDisplayName(),
                    MediaUrls.of(e.getActorAvatarMediaId()),
                    e.getTerritoryId(),
                    e.getTerritoryName(),
                    e.getCreatedAt());
        }
    }
}
