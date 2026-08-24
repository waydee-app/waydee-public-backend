package com.waydee.territory.application.event;

import java.util.Map;
import java.util.UUID;

/**
 * Alan satın alındığında yayınlanan domain event.
 * feature: haritaya doğrudan eklenebilir GeoJSON Feature (commit sonrası
 * DB erişimi gerekmesin diye event üretilirken hazırlanır).
 */
public record TerritoryPurchasedEvent(
        UUID territoryId,
        UUID buyerId,
        String buyerUsername,
        Map<String, Object> feature
) {
}
