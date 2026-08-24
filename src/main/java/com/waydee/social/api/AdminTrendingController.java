package com.waydee.social.api;

import com.waydee.social.api.dto.TrendingDtos.TrendingResponse;
import com.waydee.social.application.TrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Trend sıralamasının yönetim yüzeyi.
 *
 * Sıralama normalde 10 dakikada bir kendi kendine hesaplanır; bu uç yönetimin
 * bir kampanya ya da müdahale sonrası <b>beklemeden</b> tazeleyebilmesi içindir.
 */
@Tag(name = "Admin · Trending", description = "Trend sıralaması yönetimi")
@RestController
@RequestMapping("/api/v1/admin/trending")
@RequiredArgsConstructor
public class AdminTrendingController {

    private final TrendingService trendingService;

    @Operation(summary = "Trend skorlarını şimdi yeniden hesapla")
    @PostMapping("/recompute")
    public Map<String, Integer> recompute() {
        return Map.of("entries", trendingService.recompute());
    }

    @Operation(summary = "Hesaplanmış sıralamayı bileşenleriyle gör")
    @GetMapping
    public TrendingResponse current() {
        return new TrendingResponse(trendingService.territories(20), trendingService.users(20));
    }
}
