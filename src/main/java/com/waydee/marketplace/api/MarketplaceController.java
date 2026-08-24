package com.waydee.marketplace.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingResponse;
import com.waydee.marketplace.api.dto.MarketplaceDtos.MarketplaceResponse;
import com.waydee.marketplace.api.dto.MarketplaceDtos.OptionView;
import com.waydee.marketplace.application.ListingService;
import com.waydee.marketplace.application.MarketplaceMapService;
import com.waydee.marketplace.application.MarketplaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Map;
import java.util.UUID;

/** Pazar yeri — üye tarafı (oturum gerekir). */
@Tag(name = "Marketplace", description = "Pazar yerleri ve stantlar")
@RestController
@RequestMapping("/api/v1/marketplaces")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final ListingService listingService;
    private final MarketplaceMapService mapService;
    private final com.waydee.marketplace.application.MarketplaceWorldService worldService;
    private final com.waydee.marketplace.application.FormSchemaService formSchemaService;

    @Operation(summary = "Yayındaki pazar yerleri")
    @GetMapping
    public List<MarketplaceResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return marketplaceService.listPublic(principal != null ? principal.id() : null);
    }

    @Operation(summary = "Pazar yeri detayı (kısa ad ile)")
    @GetMapping("/{slug}")
    public MarketplaceResponse bySlug(@PathVariable String slug,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return marketplaceService.getBySlug(slug, principal != null ? principal.id() : null);
    }

    /**
     * 🔴 <b>3B evren</b> (11 Ağu 2026) — aynı onaylı stantlar, gezilebilir bir
     * caddeye dizilmiş hâlde. Harita uçlarından ({@code /map/stalls}) ayrı
     * durur: o gerçek dünya koordinatı verir, bu <b>metre</b> cinsinden yerel
     * sahne koordinatı.
     */
    @Operation(summary = "Pazar yerinin 3B evreni (mağazalar + raf gönderileri)")
    @GetMapping("/{slug}/world")
    public com.waydee.marketplace.application.MarketplaceWorldService.WorldView world(
            @PathVariable String slug,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return worldService.build(slug, principal != null ? principal.id() : null);
    }

    @Operation(summary = "Bir pazardaki onaylı stantlar")
    @GetMapping("/{marketplaceId}/listings")
    public List<ListingResponse> listings(@PathVariable UUID marketplaceId,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return listingService.approved(marketplaceId, principal != null ? principal.id() : null);
    }

    @Operation(summary = "Pazar yerine başvur (stant aç)")
    @PostMapping("/{marketplaceId}/listings")
    public ResponseEntity<ListingResponse> apply(@PathVariable UUID marketplaceId,
                                                 @AuthenticationPrincipal AuthenticatedUser principal,
                                                 @Valid @RequestBody ListingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(listingService.apply(marketplaceId, principal.id(), request));
    }

    @Operation(summary = "Harita katmanı — pazar alanları (poligon)")
    @GetMapping("/map/areas")
    public Map<String, Object> areas() {
        return mapService.areasAsGeoJson();
    }

    @Operation(summary = "Harita katmanı — onaylı stantlar (nokta, kümelenir)")
    @GetMapping("/map/stalls")
    public Map<String, Object> stalls() {
        return mapService.stallsAsGeoJson();
    }

    @Operation(summary = "Bu pazarın başvuru formu — istemci formu bundan çizer")
    @GetMapping("/{marketplaceId}/form")
    public com.waydee.marketplace.api.dto.MarketplaceDtos.ResolvedForm form(@PathVariable UUID marketplaceId) {
        return formSchemaService.resolve(marketplaceService.requirePublic(marketplaceId));
    }

    @Operation(summary = "Kategori ve aşama seçenekleri")
    @GetMapping("/options")
    public Map<String, List<OptionView>> options() {
        return Map.of("categories", OptionView.categories(), "stages", OptionView.stages());
    }
}
