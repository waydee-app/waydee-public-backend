package com.waydee.marketplace.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingResponse;
import com.waydee.marketplace.api.dto.MarketplaceDtos.MarketplaceRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.MarketplaceResponse;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ReviewRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.KindOption;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ResolvedForm;
import com.waydee.marketplace.api.dto.MarketplaceReportDtos.ReportResponse;
import com.waydee.marketplace.application.FormSchemaService;
import com.waydee.marketplace.application.ListingService;
import com.waydee.marketplace.application.MarketplaceReportService;
import com.waydee.marketplace.application.MarketplaceService;
import com.waydee.marketplace.domain.MarketplaceKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pazar yeri yönetimi — yalnız ADMIN (`/admin/**` zaten rol korumalı). */
@Tag(name = "Admin · Marketplace", description = "Pazar yeri ve stant yönetimi")
@RestController
@RequestMapping("/api/v1/admin/marketplaces")
@RequiredArgsConstructor
public class AdminMarketplaceController {

    private final MarketplaceService marketplaceService;
    private final ListingService listingService;
    private final MarketplaceReportService reportService;
    private final FormSchemaService formSchemaService;

    @Operation(summary = "Tüm pazar yerleri (taslak ve arşiv dahil)")
    @GetMapping
    public List<MarketplaceResponse> list() {
        return marketplaceService.listAll();
    }

    @Operation(summary = "Pazar yeri detayı")
    @GetMapping("/{id}")
    public MarketplaceResponse get(@PathVariable UUID id) {
        return marketplaceService.get(id, null);
    }

    @Operation(summary = "Pazar yeri oluştur (haritada poligon çiz)")
    @PostMapping
    public ResponseEntity<MarketplaceResponse> create(@Valid @RequestBody MarketplaceRequest request,
                                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(marketplaceService.create(request, actor));
    }

    @Operation(summary = "Pazar yerini güncelle")
    @PutMapping("/{id}")
    public MarketplaceResponse update(@PathVariable UUID id,
                                      @Valid @RequestBody MarketplaceRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser actor) {
        return marketplaceService.update(id, request, actor);
    }

    @Operation(summary = "Pazar yerini arşivle (veri silinmez)")
    @DeleteMapping("/{id}")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        marketplaceService.archive(id, actor);
    }

    // ------------------------------------------------------------ başvurular

    @Operation(summary = "Başvuru kuyruğu (varsayılan: bekleyenler)")
    @GetMapping("/listings")
    public PageResponse<ListingResponse> review(@RequestParam(required = false) UUID marketplaceId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return listingService.review(marketplaceId, status, PageRequest.of(page, Math.min(size, 100)));
    }

    @Operation(summary = "Bekleyen başvuru sayısı (menü rozeti)")
    @GetMapping("/listings/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", listingService.pendingCount());
    }

    @Operation(summary = "Başvuruyu onayla ya da reddet")
    @PostMapping("/listings/{listingId}/review")
    public ListingResponse decide(@PathVariable UUID listingId,
                                  @Valid @RequestBody ReviewRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        return listingService.decide(listingId, request, actor);
    }

    @Operation(summary = "Pazar yeri raporu")
    @GetMapping("/report")
    public ReportResponse report(@RequestParam(defaultValue = "30") int days) {
        return reportService.report(days);
    }

    @Operation(summary = "Pazar yeri türleri ve varsayılan alanları")
    @GetMapping("/kinds")
    public List<KindOption> kinds() {
        return java.util.Arrays.stream(MarketplaceKind.values())
                .map(k -> new KindOption(k.name(), k.label(), k.hint(),
                        k.defaultFields().stream().map(Enum::name).toList()))
                .toList();
    }

    @Operation(summary = "Bir pazarın çözülmüş başvuru formu (önizleme)")
    @GetMapping("/{id}/form")
    public ResolvedForm form(@PathVariable UUID id) {
        return formSchemaService.resolve(marketplaceService.require(id));
    }

    @Operation(summary = "Stantı öne çıkar / kaldır")
    @PostMapping("/listings/{listingId}/featured")
    public ListingResponse feature(@PathVariable UUID listingId,
                                   @RequestParam boolean value,
                                   @AuthenticationPrincipal AuthenticatedUser actor) {
        return listingService.setFeatured(listingId, value, actor);
    }
}
