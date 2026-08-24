package com.waydee.messaging.application.event;

import java.util.Map;
import java.util.UUID;

/**
 * Mesaj kaydedildikten sonra yayınlanır. payload, WS teslimi için hazır
 * MessageResponse alanlarını taşır — AFTER_COMMIT dinleyicisi DB'ye dokunmaz
 * (TerritoryPurchasedEvent ile aynı desen).
 */
public record MessageSentEvent(
        UUID recipientId,
        UUID senderId,
        Map<String, Object> payload
) {
}
