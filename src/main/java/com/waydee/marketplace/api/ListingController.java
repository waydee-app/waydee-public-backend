package com.waydee.marketplace.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingRequest;
import com.waydee.marketplace.api.dto.MarketplaceDtos.ListingResponse;
import com.waydee.marketplace.application.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

/** Stant — sahibi ve okuyucu uçları. */
@Tag(name = "Marketplace Listings", description = "Stant başvuruları")
@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    @Operation(summary = "Benim stantlarım / başvurularım")
    @GetMapping("/my")
    public List<ListingResponse> mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return listingService.myListings(principal.id());
    }

    @Operation(summary = "Stant detayı")
    @GetMapping("/{id}")
    public ListingResponse detail(@PathVariable UUID id,
                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        return listingService.detail(id, principal != null ? principal.id() : null);
    }

    @Operation(summary = "Stantımı güncelle (onaylıysa yeniden incelemeye düşer)")
    @PutMapping("/{id}")
    public ListingResponse update(@PathVariable UUID id,
                                  @AuthenticationPrincipal AuthenticatedUser principal,
                                  @Valid @RequestBody ListingRequest request) {
        return listingService.updateMine(id, principal.id(), request);
    }

    @Operation(summary = "Başvurumu geri çek")
    @DeleteMapping("/{id}")
    public void withdraw(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        listingService.withdraw(id, principal.id());
    }

    @Operation(summary = "Stantı beğen")
    @PostMapping("/{id}/like")
    public ListingResponse like(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return listingService.like(id, principal.id(), true);
    }

    @Operation(summary = "Beğeniyi geri al")
    @DeleteMapping("/{id}/like")
    public ListingResponse unlike(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return listingService.like(id, principal.id(), false);
    }
}
