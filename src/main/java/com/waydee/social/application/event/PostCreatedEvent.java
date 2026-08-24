package com.waydee.social.application.event;

import java.util.UUID;

/**
 * Gönderi oluşturulduğunda yayınlanır — aktivite akışı beslemesi.
 * kind: STANDARD | POLL | EVENT; hasMedia: fotoğraflı paylaşım ayrımı için.
 */
public record PostCreatedEvent(
        UUID postId,
        UUID territoryId,
        String territoryName,
        UUID authorId,
        String kind,
        boolean hasMedia
) {
}
