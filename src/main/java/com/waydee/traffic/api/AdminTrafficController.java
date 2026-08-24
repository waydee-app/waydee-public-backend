package com.waydee.traffic.api;

import com.waydee.common.web.PageResponse;
import com.waydee.traffic.api.dto.TrafficDtos.LoginRow;
import com.waydee.traffic.api.dto.TrafficDtos.TrafficOverview;
import com.waydee.traffic.application.TrafficService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin Traffic", description = "Trafik kontrol: girişler, ülkeler, cihazlar")
@RestController
@RequestMapping("/api/v1/admin/traffic")
@RequiredArgsConstructor
public class AdminTrafficController {

    private final TrafficService trafficService;

    @Operation(summary = "Trafik özeti (ülke/cihaz/tarayıcı dağılımı + günlük grafik)")
    @GetMapping
    public TrafficOverview overview(@RequestParam(defaultValue = "30") int days) {
        return trafficService.overview(days);
    }

    @Operation(summary = "Giriş kayıtları (kullanıcı filtreli)")
    @GetMapping("/logins")
    public PageResponse<LoginRow> logins(@RequestParam(required = false) UUID userId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "30") int size) {
        return trafficService.logins(userId, page, size);
    }
}
