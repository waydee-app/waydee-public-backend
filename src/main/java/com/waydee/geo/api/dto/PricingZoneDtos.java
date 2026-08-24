package com.waydee.geo.api.dto;

import com.waydee.common.geo.GeoJson;
import com.waydee.geo.domain.PricingZone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PricingZoneDtos {

    private PricingZoneDtos() {
    }

    public record PricingZoneRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 300) String description,

            /** Serbest çizim halkası: [[lng, lat], ...] — en az 3 nokta. */
            @NotNull
            @NotEmpty(message = "Bölge sınırı çizilmeli")
            @Size(min = 3, max = 500, message = "Poligon 3-500 nokta içermeli")
            List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> ring,

            @NotNull @PositiveOrZero BigDecimal pricePerKm2,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Para birimi 3 harfli kod olmalı") String currency,
            @Min(0) @Max(10_000) Integer priority,
            Boolean active
    ) {
    }

    public record PricingZoneResponse(
            UUID id,
            String name,
            String description,
            BigDecimal areaKm2,
            BigDecimal pricePerKm2,
            String currency,
            int priority,
            boolean active,
            List<List<Double>> ring,
            Instant createdAt
    ) {
        public static PricingZoneResponse from(PricingZone zone) {
            List<List<Double>> ring = java.util.Arrays.stream(zone.getBoundary().getExteriorRing().getCoordinates())
                    .map(c -> List.of(round(c.x), round(c.y)))
                    .toList();
            return new PricingZoneResponse(zone.getId(), zone.getName(), zone.getDescription(),
                    zone.getAreaKm2(), zone.getPricePerKm2(), zone.getCurrency(),
                    zone.getPriority(), zone.isActive(), ring, zone.getCreatedAt());
        }

        private static double round(double value) {
            return Math.round(value * 1_000_000.0) / 1_000_000.0;
        }
    }

    public static Map<String, Object> toFeature(PricingZone zone) {
        return GeoJson.feature("zone-" + zone.getId(), GeoJson.polygon(zone.getBoundary()), Map.of(
                "id", zone.getId().toString(),
                "level", "ZONE",
                "name", zone.getName(),
                "pricePerKm2", zone.getPricePerKm2(),
                "currency", zone.getCurrency(),
                "priority", zone.getPriority()));
    }
}
