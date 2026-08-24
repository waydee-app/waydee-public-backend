package com.waydee.territory.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.territory.api.dto.TerritoryDtos.CreateStoreRequest;
import com.waydee.territory.api.dto.TerritoryDtos.TerritoryResponse;
import com.waydee.territory.api.dto.TerritoryDtos.UpdateTerritoryRequest;
import com.waydee.territory.application.TerritoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Territories", description = "Mağaza kurma ve görünüm düzenleme")
@RestController
@RequestMapping("/api/v1/territories")
@RequiredArgsConstructor
public class TerritoryController {

    private final TerritoryService territoryService;
    /** 🔒 17 Ağu 2026 — denetim kaydına yazılan IP artık sahte X-Forwarded-For ile ezilemez. */
    private final com.waydee.common.net.ClientIpResolver clientIpResolver;

    /**
     * <b>Mağazamı kur</b> (V38).
     *
     * <p>🔴 Fiyat teklifi ucu (`POST /quote`) ve bölge ödeme oturumu
     * <b>kaldırıldı</b>: daire artık km² üzerinden satılan bir ürün değil,
     * <b>Premium üyeliğin hakkıdır</b>. Ödeme adımı olmadığı için webhook
     * beklemeye de gerek yok — mağaza tek istekte oluşur.
     *
     * <p>⚠️ Yarıçap <b>istekte yok</b>: sunucu 100 m uygular
     * ({@code TerritoryService.STORE_RADIUS_M}). İstemciden almak, sabit
     * yarıçapı istemci tarafı bir süse çevirirdi.
     */
    @Operation(summary = "Mağazamı kur (Premium · 100 m sabit yarıçap)")
    @PostMapping("/store")
    @ResponseStatus(HttpStatus.CREATED)
    public TerritoryResponse createStore(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @Valid @RequestBody CreateStoreRequest request,
                                         jakarta.servlet.http.HttpServletRequest http) {
        return territoryService.createStore(principal.id(), request.lng(), request.lat(),
                request.name(), request.style(), request.categoryId(), clientIpResolver.resolve(http));
    }

    /**
     * <b>Mağazamı sil</b> (24 Ağu 2026).
     *
     * <p>⚠️ {@code 204} döner ve <b>idempotenttir</b>: zaten silinmiş bir
     * mağaza için de 204 verir. Çift tıklayan istemciye 404 göstermek,
     * başarılı bir işlemi hata gibi okutmak olurdu.
     */
    @Operation(summary = "Mağazamı sil")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStore(@AuthenticationPrincipal AuthenticatedUser principal,
                            @PathVariable UUID id,
                            jakarta.servlet.http.HttpServletRequest http) {
        territoryService.deleteOwnStore(id, principal.id(), clientIpResolver.resolve(http));
    }

    @Operation(summary = "Kendi alanlarım")
    @GetMapping("/my")
    public List<TerritoryResponse> myTerritories(@AuthenticationPrincipal AuthenticatedUser principal) {
        return territoryService.myTerritories(principal.id());
    }

    @Operation(summary = "Bir kullanıcının alanları")
    @GetMapping("/user/{userId}")
    public List<TerritoryResponse> userTerritories(@PathVariable UUID userId) {
        return territoryService.userTerritories(userId);
    }

    @Operation(summary = "Alan detayı")
    @GetMapping("/{id}")
    public TerritoryResponse get(@PathVariable UUID id) {
        return territoryService.get(id);
    }

    @Operation(summary = "Alan adı/görünümünü güncelle (yalnız sahibi)")
    @PatchMapping("/{id}")
    public TerritoryResponse update(@PathVariable UUID id,
                                    @AuthenticationPrincipal AuthenticatedUser principal,
                                    @Valid @RequestBody UpdateTerritoryRequest request) {
        return territoryService.update(id, principal.id(), request);
    }

    // ⚠️ Yenileme ucu da ödeme akışına taşındı:
    // POST /api/v1/payments/territory-renewal-checkout
}
