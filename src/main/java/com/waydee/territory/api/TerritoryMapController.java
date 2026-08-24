package com.waydee.territory.api;

import com.waydee.territory.application.TerritoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Map", description = "Harita katmanları (GeoJSON)")
@RestController
@RequestMapping("/api/v1/map")
@RequiredArgsConstructor
public class TerritoryMapController {

    private final TerritoryService territoryService;

    @Operation(summary = "Sahipli alanlar (GeoJSON FeatureCollection)")
    @GetMapping("/territories")
    public Map<String, Object> territories() {
        return territoryService.territoriesAsGeoJson();
    }
}
