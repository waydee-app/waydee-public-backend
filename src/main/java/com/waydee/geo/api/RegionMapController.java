package com.waydee.geo.api;

import com.waydee.geo.application.RegionQueryService;
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
public class RegionMapController {

    private final RegionQueryService regionQueryService;

    @Operation(summary = "Satışa açık bölgeler ve efektif fiyatlar (GeoJSON FeatureCollection)")
    @GetMapping("/regions")
    public Map<String, Object> regions() {
        return regionQueryService.regionsAsGeoJson();
    }
}
