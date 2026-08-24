package com.waydee.territory.application.event;

import java.util.Map;
import java.util.UUID;

/**
 * Bölgenin adı/görünümü değiştiğinde yayınlanır — haritalar anında günceller.
 * feature: hazır GeoJSON (AFTER_COMMIT dinleyicisi DB'ye dokunmaz).
 */
public record TerritoryStyleChangedEvent(UUID territoryId, Map<String, Object> feature) {
}
