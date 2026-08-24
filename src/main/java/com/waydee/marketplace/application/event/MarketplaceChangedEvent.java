package com.waydee.marketplace.application.event;

import java.util.UUID;

/**
 * Pazar yeri ya da stantları değişti — açık haritalar tazelensin.
 *
 * @param action CREATED | UPDATED | REMOVED | LISTING_APPROVED | LISTING_REMOVED
 */
public record MarketplaceChangedEvent(UUID marketplaceId, String action) {
}
