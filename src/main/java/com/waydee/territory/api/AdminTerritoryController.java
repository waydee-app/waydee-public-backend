package com.waydee.territory.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.territory.api.dto.TerritoryDtos.AdminReserveRequest;
import com.waydee.territory.api.dto.TerritoryDtos.AdminTerritoryResponse;
import com.waydee.territory.api.dto.TerritoryDtos.AdminUpdateTerritoryRequest;
import com.waydee.territory.application.TerritoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Bölge yönetimi (yalnız ADMIN — /api/v1/admin/** zaten rol kapılı).
 * Haritada dolaşıp bölgeleri silme (pasife alma), sahiplik devri, görünürlük,
 * rezerve alan tanımlama ve görünüm özelleştirmesi buradan yapılır.
 */
@Tag(name = "Admin Territories", description = "Bölge yönetimi")
@RestController
@RequestMapping("/api/v1/admin/territories")
@RequiredArgsConstructor
public class AdminTerritoryController {

    private final TerritoryService territoryService;

    @Operation(summary = "Bölgeleri listele (gizli/pasif dahil)")
    @GetMapping
    public PageResponse<AdminTerritoryResponse> list(@RequestParam(required = false) String query,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return territoryService.adminList(query, page, size);
    }

    @Operation(summary = "Yönetim haritası (GeoJSON) — gizli bölgeler de döner")
    @GetMapping("/map")
    public Map<String, Object> map() {
        return territoryService.adminTerritoriesAsGeoJson();
    }

    @Operation(summary = "Bölgeyi güncelle: ad, sahip devri, görünürlük, rezerve, durum, görünüm")
    @PatchMapping("/{id}")
    public AdminTerritoryResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody AdminUpdateTerritoryRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return territoryService.adminUpdate(id, request, actor);
    }

    @Operation(summary = "Rezerve alan oluştur (sahibi belirsiz kurum bölgesi)")
    @PostMapping("/reserve")
    public AdminTerritoryResponse reserve(@Valid @RequestBody AdminReserveRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser actor) {
        return territoryService.adminReserve(request, actor);
    }
}
