package com.waydee.moderation.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.moderation.api.dto.ModerationDtos.OptionResponse;
import com.waydee.moderation.api.dto.ModerationDtos.ReportResponse;
import com.waydee.moderation.api.dto.ModerationDtos.ResolveReportRequest;
import com.waydee.moderation.api.dto.ModerationDtos.RestrictionRequest;
import com.waydee.moderation.api.dto.ModerationDtos.RestrictionResponse;
import com.waydee.moderation.application.ReportService;
import com.waydee.moderation.application.RestrictionService;
import com.waydee.moderation.domain.RestrictedAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Yönetim: şikayet inceleme + kullanıcı kısıtlamaları. */
@Tag(name = "Admin Moderation", description = "Şikayetler ve kullanıcı kısıtlamaları")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ReportService reportService;
    private final RestrictionService restrictionService;

    // ------------------------------------------------------------ şikayetler

    @Operation(summary = "Şikayetler (status: OPEN/REVIEWING/RESOLVED/REJECTED/ALL)")
    @GetMapping("/reports")
    public PageResponse<ReportResponse> reports(@RequestParam(defaultValue = "OPEN") String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return reportService.list(status, page, size);
    }

    @Operation(summary = "Açık şikayet sayısı (rozet)")
    @GetMapping("/reports/open-count")
    public java.util.Map<String, Long> openCount() {
        return java.util.Map.of("count", reportService.openCount());
    }

    @Operation(summary = "Şikayeti sonuçlandır")
    @PostMapping("/reports/{reportId}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID reportId,
                                        @AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody ResolveReportRequest request) {
        reportService.resolve(reportId, request.status(), request.note(), principal.id(), principal.username());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ kısıtlamalar

    @Operation(summary = "Kısıtlanabilir eylemler")
    @GetMapping("/restriction-actions")
    public List<OptionResponse> actions() {
        return Arrays.stream(RestrictedAction.values())
                .map(a -> new OptionResponse(a.name(), a.label()))
                .toList();
    }

    @Operation(summary = "Kullanıcının aktif kısıtlamaları")
    @GetMapping("/users/{userId}/restrictions")
    public List<RestrictionResponse> restrictions(@PathVariable UUID userId) {
        return restrictionService.listActive(userId).stream().map(RestrictionResponse::from).toList();
    }

    @Operation(summary = "Kullanıcıya kısıtlama uygula")
    @PostMapping("/users/{userId}/restrictions")
    public RestrictionResponse restrict(@PathVariable UUID userId,
                                        @AuthenticationPrincipal AuthenticatedUser principal,
                                        @Valid @RequestBody RestrictionRequest request) {
        RestrictedAction action;
        try {
            action = RestrictedAction.valueOf(request.action());
        } catch (IllegalArgumentException ex) {
            throw com.waydee.common.error.ApiException.badRequest("Geçersiz eylem");
        }
        return RestrictionResponse.from(
                restrictionService.restrict(userId, action, request.reason(), principal.id(), request.expiresAt()));
    }

    @Operation(summary = "Kısıtlamayı kaldır")
    @DeleteMapping("/users/{userId}/restrictions/{action}")
    public ResponseEntity<Void> lift(@PathVariable UUID userId, @PathVariable String action) {
        try {
            restrictionService.lift(userId, RestrictedAction.valueOf(action));
        } catch (IllegalArgumentException ex) {
            throw com.waydee.common.error.ApiException.badRequest("Geçersiz eylem");
        }
        return ResponseEntity.noContent().build();
    }
}
