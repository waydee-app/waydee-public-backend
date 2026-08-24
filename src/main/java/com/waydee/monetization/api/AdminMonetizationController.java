package com.waydee.monetization.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.monetization.api.dto.MonetizationDtos.AdminRequestResponse;
import com.waydee.monetization.api.dto.MonetizationDtos.DecisionRequest;
import com.waydee.monetization.application.MonetizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Yönetim ucu — gelir başvurularını listeler ve karara bağlar.
 *
 * <p>⚠️ Yol <code>/admin/**</code> altındadır; `SecurityConfig` bu önekin
 * tamamına <b>hasRole('ADMIN')</b> uygular. Burada ayrıca `@PreAuthorize`
 * yazmak gerekmez ama yol DEĞİŞTİRİLEMEZ — önek dışına taşınırsa yetki
 * kontrolü sessizce kaybolur.
 */
@Tag(name = "Admin · Monetization", description = "Gelir başvurusu yönetimi")
@RestController
@RequestMapping("/api/v1/admin/monetization")
@RequiredArgsConstructor
public class AdminMonetizationController {

    private final MonetizationService service;

    @Operation(summary = "Başvuruları listele")
    @GetMapping("/requests")
    public PageResponse<AdminRequestResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    @Operation(summary = "Bekleyen başvuru sayısı (menü rozeti)")
    @GetMapping("/requests/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", service.pendingCount());
    }

    @Operation(summary = "Başvuruyu karara bağla")
    @PatchMapping("/requests/{id}")
    public AdminRequestResponse decide(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody DecisionRequest request) {
        return service.decide(principal.id(), id, request.status(), request.note());
    }
}
