package com.waydee.marketplace.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.marketplace.api.dto.StoreStudioDtos.ReorderRequest;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StoreProductRequest;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StoreProductView;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StudioSettingsRequest;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StudioSettingsView;
import com.waydee.marketplace.api.dto.StoreStudioDtos.StudioView;
import com.waydee.marketplace.application.StoreStudioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * <b>Mağaza stüdyosu</b> uçları — kabul edilen stant sahibinin paneli.
 *
 * <p>Hepsi oturum ister ve sahiplik {@code StoreStudioService} içinde
 * doğrulanır (istemcinin düğmeyi gizlemesi güvenlik sayılmaz — vault kuralı).
 */
@RestController
@RequestMapping("/api/v1/marketplace/listings/{listingId}/studio")
@RequiredArgsConstructor
public class StoreStudioController {

    private final StoreStudioService studioService;

    @GetMapping
    public StudioView studio(@PathVariable UUID listingId,
                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.studio(listingId, principal.id());
    }

    @PatchMapping("/settings")
    public StudioSettingsView updateSettings(@PathVariable UUID listingId,
                                             @Valid @RequestBody StudioSettingsRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.updateSettings(listingId, principal.id(), request);
    }

    @DeleteMapping("/settings/video")
    public StudioSettingsView clearVideo(@PathVariable UUID listingId,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.clearVideo(listingId, principal.id());
    }

    @DeleteMapping("/settings/music")
    public StudioSettingsView clearMusic(@PathVariable UUID listingId,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.clearMusic(listingId, principal.id());
    }

    @PostMapping("/products")
    public StoreProductView addProduct(@PathVariable UUID listingId,
                                       @Valid @RequestBody StoreProductRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.addProduct(listingId, principal.id(), request);
    }

    @PutMapping("/products/{productId}")
    public StoreProductView updateProduct(@PathVariable UUID listingId,
                                          @PathVariable UUID productId,
                                          @Valid @RequestBody StoreProductRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.updateProduct(listingId, productId, principal.id(), request);
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Void> removeProduct(@PathVariable UUID listingId,
                                              @PathVariable UUID productId,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        studioService.removeProduct(listingId, productId, principal.id());
        return ResponseEntity.noContent().build();
    }

    /** Sürükle-bırak sıralaması tek istekte kaydedilir. */
    @PutMapping("/products/order")
    public List<StoreProductView> reorder(@PathVariable UUID listingId,
                                          @Valid @RequestBody ReorderRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return studioService.reorder(listingId, principal.id(), request);
    }
}
