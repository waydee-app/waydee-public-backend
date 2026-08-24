package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.AnalyticsDtos.AnalyticsResponse;
import com.waydee.social.application.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Analytics", description = "Görüntülenme raporları")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Bir bölgenin görüntülenme raporu (yalnızca sahibi)")
    @GetMapping("/territories/{territoryId}/analytics")
    public AnalyticsResponse territoryAnalytics(@PathVariable UUID territoryId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return analyticsService.territoryAnalytics(territoryId, principal.id());
    }

    @Operation(summary = "Tüm bölgelerimin toplam görüntülenme raporu")
    @GetMapping("/me/analytics")
    public AnalyticsResponse myAnalytics(@AuthenticationPrincipal AuthenticatedUser principal) {
        return analyticsService.myAnalytics(principal.id());
    }

    @Operation(summary = "Tam rapor — trend, bölge kırılımı, kitle ve etkileşim")
    @GetMapping("/me/report")
    public com.waydee.social.api.dto.AnalyticsDtos.ReportResponse myReport(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "14") int days) {
        return analyticsService.report(principal.id(), days);
    }
}
