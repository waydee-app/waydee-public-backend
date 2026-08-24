package com.waydee.territory.application.event;

import java.util.UUID;

/**
 * Bölge haritadan kalktı (pasife alındı ya da admin tarafından gizlendi).
 * İstemciler feature'ı kaynaklarından siler — payload'a ihtiyaç yok.
 */
public record TerritoryRemovedEvent(UUID territoryId) {
}
