package com.waydee.geo.application.event;

import java.util.Map;
import java.util.UUID;

/**
 * Admin bir fiyat bölgesi oluşturduğunda/güncellediğinde/sildiğinde yayınlanır.
 * feature: haritaya doğrudan basılabilir GeoJSON (silme/pasifte null) —
 * AFTER_COMMIT dinleyicisi DB'ye dokunmasın diye event üretilirken hazırlanır.
 * action: CREATED | UPDATED | DELETED | DEACTIVATED
 */
public record PricingZoneChangedEvent(
        String action,
        UUID zoneId,
        Map<String, Object> feature
) {
}
