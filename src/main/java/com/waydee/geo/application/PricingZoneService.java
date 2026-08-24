package com.waydee.geo.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.common.geo.GeoUtils;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.geo.api.dto.PricingZoneDtos;
import com.waydee.geo.api.dto.PricingZoneDtos.PricingZoneRequest;
import com.waydee.geo.api.dto.PricingZoneDtos.PricingZoneResponse;
import com.waydee.geo.application.event.PricingZoneChangedEvent;
import com.waydee.geo.domain.PricingZone;
import com.waydee.geo.infrastructure.PricingZoneRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingZoneService {

    private static final int DEFAULT_PRIORITY = 100;

    private final PricingZoneRepository repository;
    private final AuditRecorder auditRecorder;
    private final DomainEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<PricingZoneResponse> list() {
        return repository.findAllByOrderByPriorityDescCreatedAtDesc().stream()
                .map(PricingZoneResponse::from)
                .toList();
    }

    @Transactional
    public PricingZoneResponse create(PricingZoneRequest request, AuthenticatedUser actor) {
        Polygon boundary = buildPolygon(request);
        BigDecimal areaKm2 = GeoUtils.polygonAreaKm2(boundary);

        PricingZone zone = repository.save(new PricingZone(
                request.name().trim(),
                blankToNull(request.description()),
                boundary,
                areaKm2,
                request.pricePerKm2(),
                request.currency(),
                request.priority() != null ? request.priority() : DEFAULT_PRIORITY));

        audit(actor, "PRICING_ZONE_CREATED", zone);
        // Kullanıcı haritaları anında güncellensin (pasif bölge haritada gösterilmez).
        eventPublisher.publish(new PricingZoneChangedEvent("CREATED", zone.getId(),
                zone.isActive() ? PricingZoneDtos.toFeature(zone) : null));
        return PricingZoneResponse.from(zone);
    }

    @Transactional
    public PricingZoneResponse update(UUID id, PricingZoneRequest request, AuthenticatedUser actor) {
        PricingZone zone = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fiyatlandırma bölgesi bulunamadı"));

        Polygon boundary = buildPolygon(request);
        zone.reshape(boundary, GeoUtils.polygonAreaKm2(boundary));
        zone.setName(request.name().trim());
        zone.setDescription(blankToNull(request.description()));
        zone.setPricePerKm2(request.pricePerKm2());
        zone.setCurrency(request.currency());
        if (request.priority() != null) {
            zone.setPriority(request.priority());
        }
        if (request.active() != null) {
            zone.setActive(request.active());
        }

        audit(actor, "PRICING_ZONE_UPDATED", zone);
        // Pasifleştirilen bölge haritadan KALDIRILMALI (RegionQueryService yalnız aktifleri döner).
        eventPublisher.publish(new PricingZoneChangedEvent(
                zone.isActive() ? "UPDATED" : "DEACTIVATED",
                zone.getId(),
                zone.isActive() ? PricingZoneDtos.toFeature(zone) : null));
        return PricingZoneResponse.from(zone);
    }

    @Transactional
    public void delete(UUID id, AuthenticatedUser actor) {
        PricingZone zone = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fiyatlandırma bölgesi bulunamadı"));
        repository.delete(zone);
        auditRecorder.record(actor.id(), actor.username(), "PRICING_ZONE_DELETED", "PRICING_ZONE",
                id.toString(), Map.of("name", zone.getName()), null);
        eventPublisher.publish(new PricingZoneChangedEvent("DELETED", id, null));
    }

    private Polygon buildPolygon(PricingZoneRequest request) {
        List<double[]> ring = request.ring().stream()
                .map(point -> new double[]{point.get(0), point.get(1)})
                .toList();
        for (double[] point : ring) {
            if (point[0] < -180 || point[0] > 180 || point[1] < -90 || point[1] > 90) {
                throw ApiException.badRequest("Poligon koordinatları geçerli aralıkta olmalı");
            }
        }
        try {
            return GeoUtils.polygon(ring);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void audit(AuthenticatedUser actor, String action, PricingZone zone) {
        auditRecorder.record(actor.id(), actor.username(), action, "PRICING_ZONE", zone.getId().toString(),
                Map.of("name", zone.getName(),
                        "pricePerKm2", zone.getPricePerKm2().toPlainString(),
                        "priority", zone.getPriority()), null);
    }
}
