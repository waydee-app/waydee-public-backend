package com.waydee.monetization.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.monetization.api.dto.MonetizationDtos.CreateRequest;
import com.waydee.monetization.api.dto.MonetizationDtos.MyRequestResponse;
import com.waydee.monetization.application.MonetizationService;
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

/**
 * Kullanıcı ucu — "Etkini gelire dönüştür" kartı bunu çağırır.
 *
 * <p>⚠️ Kimlik <b>oturumdan</b> alınır; istekte kullanıcı kimliği KABUL
 * EDİLMEZ. Aksi halde herkes başkası adına başvuru gönderebilirdi.
 */
@Tag(name = "Monetization", description = "Gelir başvurusu")
@RestController
@RequestMapping("/api/v1/monetization")
@RequiredArgsConstructor
public class MonetizationController {

    private final MonetizationService service;

    @Operation(summary = "Gelir başvurusu gönder")
    @PostMapping("/requests")
    public ResponseEntity<MyRequestResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(principal.id(), request));
    }

    @Operation(summary = "Kendi başvurumun durumu")
    @GetMapping("/requests/me")
    public MyRequestResponse mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.mine(principal.id());
    }
}
