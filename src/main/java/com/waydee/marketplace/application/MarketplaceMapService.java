package com.waydee.marketplace.application;

import com.waydee.common.geo.GeoJson;
import com.waydee.common.storage.MediaUrls;
import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceListing;
import com.waydee.marketplace.infrastructure.MarketplaceListingRepository;
import com.waydee.marketplace.infrastructure.MarketplaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pazar yerlerinin harita katmanı.
 *
 * İKİ kaynak üretir:
 * <ul>
 *   <li><b>alanlar</b> (Polygon) — pazarın sınırı, kendi vurgu rengiyle boyanır.</li>
 *   <li><b>stantlar</b> (Point) — onaylı stantlar. İstemci bunları Mapbox'ın
 *       yerel kümeleme desteğiyle bir arada gösterir: uzaktan tek bir "pazar
 *       rozeti", yaklaşınca stant stant ayrışır.</li>
 * </ul>
 *
 * Neden tek uç: harita açılışında iki ayrı istek yerine tek FeatureCollection
 * çifti gelir; pazar sayısı arttıkça istek sayısı sabit kalır.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceMapService {

    private final MarketplaceRepository marketplaceRepository;
    private final MarketplaceListingRepository listingRepository;

    /** Pazar alanları (poligon). Taslak/arşiv dönmez. */
    @Transactional(readOnly = true)
    public Map<String, Object> areasAsGeoJson() {
        List<Map<String, Object>> features = marketplaceRepository.findPublic().stream()
                .map(this::areaFeature)
                .toList();
        return GeoJson.featureCollection(features);
    }

    /** Onaylı stantlar (nokta) — kümeleme kaynağı. */
    @Transactional(readOnly = true)
    public Map<String, Object> stallsAsGeoJson() {
        List<Marketplace> markets = marketplaceRepository.findPublic();
        if (markets.isEmpty()) {
            return GeoJson.featureCollection(List.of());
        }
        Map<UUID, Marketplace> byId = new HashMap<>();
        markets.forEach(m -> byId.put(m.getId(), m));

        List<Map<String, Object>> features = listingRepository.findApprovedIn(byId.keySet()).stream()
                // Onaylıysa spot dolu olmalı; savunmacı kontrol (eski veri / elle müdahale).
                .filter(l -> l.getSpot() != null)
                .map(l -> stallFeature(l, byId.get(l.getMarketplaceId())))
                .toList();
        return GeoJson.featureCollection(features);
    }

    private Map<String, Object> areaFeature(Marketplace m) {
        Map<String, Object> props = new HashMap<>();
        props.put("id", m.getId().toString());
        props.put("slug", m.getSlug());
        props.put("name", m.getName());
        props.put("tagline", m.getTagline());
        props.put("accentColor", m.getAccentColor());
        props.put("status", m.getStatus().name());
        props.put("listingCount", m.getListingCount());
        props.put("centerLng", m.getCenter().getX());
        props.put("centerLat", m.getCenter().getY());
        props.put("areaKm2", m.getAreaKm2().doubleValue());
        props.put("accepting", m.acceptsApplications(java.time.Instant.now()));
        String cover = MediaUrls.of(m.getCoverMediaId());
        if (cover != null) {
            props.put("coverUrl", cover);
        }
        return GeoJson.feature(m.getId().toString(), GeoJson.polygon(m.getBoundary()), props);
    }

    private Map<String, Object> stallFeature(MarketplaceListing l, Marketplace m) {
        Map<String, Object> props = new HashMap<>();
        props.put("id", l.getId().toString());
        props.put("marketplaceId", l.getMarketplaceId().toString());
        props.put("marketplaceName", m != null ? m.getName() : null);
        props.put("marketplaceSlug", m != null ? m.getSlug() : null);
        props.put("accentColor", m != null ? m.getAccentColor() : "#8e59ff");
        props.put("title", l.getTitle());
        props.put("tagline", l.getTagline());
        props.put("category", l.getCategory().name());
        props.put("categoryLabel", l.getCategory().label());
        props.put("featured", l.isFeatured());
        props.put("likeCount", l.getLikeCount());
        props.put("ownerUsername", l.getOwner().getUsername());
        props.put("ownerDisplayName", l.getOwner().getDisplayName());
        String logo = MediaUrls.of(l.getLogoMediaId());
        if (logo != null) {
            props.put("logoUrl", logo);
        }
        String avatar = MediaUrls.of(l.getOwner().getAvatarMediaId());
        if (avatar != null) {
            props.put("ownerAvatarUrl", avatar);
        }
        return GeoJson.feature(l.getId().toString(), GeoJson.point(l.getSpot()), props);
    }
}
