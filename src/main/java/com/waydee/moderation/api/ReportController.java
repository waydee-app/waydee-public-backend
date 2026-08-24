package com.waydee.moderation.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.moderation.api.dto.ModerationDtos.CreateReportRequest;
import com.waydee.moderation.api.dto.ModerationDtos.OptionResponse;
import com.waydee.moderation.application.ReportService;
import com.waydee.moderation.domain.ReportReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Reports", description = "Kullanıcı şikayetleri")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "Kullanıcıyı şikayet et")
    @PostMapping
    public ResponseEntity<Void> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody CreateReportRequest request) {
        reportService.create(principal.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Şikayet sebepleri")
    @GetMapping("/reasons")
    public List<OptionResponse> reasons() {
        return Arrays.stream(ReportReason.values())
                .map(r -> new OptionResponse(r.name(), r.label()))
                .toList();
    }
}
