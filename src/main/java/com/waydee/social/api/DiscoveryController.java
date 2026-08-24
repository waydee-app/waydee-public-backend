package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.SearchDtos.SearchResponse;
import com.waydee.social.api.dto.TrendingDtos.TrendingResponse;
import com.waydee.social.application.SearchService;
import com.waydee.social.application.TerritoryEngagementService;
import com.waydee.social.application.TerritoryEngagementService.EngagementState;
import com.waydee.social.application.TrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Keşif yüzeyi: arama, trend duyuruları ve daire etkileşimleri. */
@Tag(name = "Discovery", description = "Arama, trend ve daire etkileşimleri")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DiscoveryController {

    private final SearchService searchService;
    private final TrendingService trendingService;
    private final TerritoryEngagementService engagementService;

    @Operation(summary = "Kullanıcı ve daire araması")
    @GetMapping("/search")
    public SearchResponse search(@RequestParam(name = "q", required = false) String query,
                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        return searchService.search(query, principal != null ? principal.id() : null);
    }

    @Operation(summary = "Yükselişte olan daireler ve profiller")
    @GetMapping("/trending")
    public TrendingResponse trending(@RequestParam(defaultValue = "8") int limit) {
        int capped = Math.min(Math.max(limit, 1), 20);
        return new TrendingResponse(trendingService.territories(capped), trendingService.users(capped));
    }

    @Operation(summary = "Daireyi beğen")
    @PostMapping("/territories/{id}/like")
    public EngagementState like(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return engagementService.like(id, principal.id(), true);
    }

    @Operation(summary = "Daire beğenisini geri al")
    @DeleteMapping("/territories/{id}/like")
    public EngagementState unlike(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return engagementService.like(id, principal.id(), false);
    }

    @Operation(summary = "Daireyi kaydet")
    @PostMapping("/territories/{id}/save")
    public EngagementState save(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return engagementService.save(id, principal.id(), true);
    }

    @Operation(summary = "Kaydetmeyi geri al")
    @DeleteMapping("/territories/{id}/save")
    public EngagementState unsave(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return engagementService.save(id, principal.id(), false);
    }
}
