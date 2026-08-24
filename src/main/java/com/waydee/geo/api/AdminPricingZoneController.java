package com.waydee.geo.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.geo.api.dto.PricingZoneDtos.PricingZoneRequest;
import com.waydee.geo.api.dto.PricingZoneDtos.PricingZoneResponse;
import com.waydee.geo.application.PricingZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin · Pricing Zones", description = "Harita üzerinde serbest çizilen fiyatlandırma bölgeleri")
@RestController
@RequestMapping("/api/v1/admin/pricing-zones")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPricingZoneController {

    private final PricingZoneService pricingZoneService;

    @Operation(summary = "Fiyatlandırma bölgelerini listele")
    @GetMapping
    public List<PricingZoneResponse> list() {
        return pricingZoneService.list();
    }

    @Operation(summary = "Serbest çizimle fiyatlandırma bölgesi oluştur")
    @PostMapping
    public ResponseEntity<PricingZoneResponse> create(@Valid @RequestBody PricingZoneRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pricingZoneService.create(request, actor));
    }

    @Operation(summary = "Fiyatlandırma bölgesini güncelle")
    @PutMapping("/{id}")
    public PricingZoneResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody PricingZoneRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return pricingZoneService.update(id, request, actor);
    }

    @Operation(summary = "Fiyatlandırma bölgesini sil")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal AuthenticatedUser actor) {
        pricingZoneService.delete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
