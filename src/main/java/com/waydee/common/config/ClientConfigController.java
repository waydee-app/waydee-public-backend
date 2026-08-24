package com.waydee.common.config;

import com.waydee.common.config.ClientConfigService.ClientConfig;
import com.waydee.common.config.ClientConfigService.MapboxTokenView;
import com.waydee.identity.application.PlanPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * İstemci yapılandırması.
 *
 * `GET /config` kimlik istemez: vitrin haritası da (oturumsuz ziyaretçi) Mapbox
 * anahtarına ihtiyaç duyar. Dönen tek şey **genel** anahtardır; gizli anahtar
 * bu yola hiç girmez (servis `sk.` değerini reddeder).
 */
@Tag(name = "Config", description = "İstemci yapılandırması")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClientConfigController {

    private final ClientConfigService configService;
    private final PlanPricingService planPricingService;

    @Operation(summary = "İstemci yapılandırması (Mapbox genel anahtarı)")
    @GetMapping("/config")
    public ClientConfig config() {
        return configService.current();
    }

    @Operation(summary = "Mapbox anahtarını görüntüle (ADMIN)")
    @GetMapping("/admin/config/mapbox")
    public MapboxTokenView adminGet() {
        return configService.adminView();
    }

    @Operation(summary = "Mapbox anahtarını güncelle — anında yayılır (ADMIN)")
    @PutMapping("/admin/config/mapbox")
    public MapboxTokenView adminUpdate(@Valid @RequestBody MapboxTokenRequest request) {
        return configService.updateMapboxToken(request.token());
    }

    /* --------------------------------------------------------- PRO ücreti
       🔴 Fiyat eskiden yalnız ortam değişkeniyle değişiyordu (yeniden dağıtım
       şart) ve tanıtım sayfasında ayrıca KODA GÖMÜLÜYDÜ. Artık tek kaynak
       `app_settings`; buradan değişir, `GET /config` ile herkese iner. */

    @Operation(summary = "Üyelik fiyat tablosunu görüntüle (ADMIN)")
    @GetMapping("/admin/config/plan-pricing")
    public PlanPricingService.PricingAdminView planPricing() {
        return planPricingService.adminView();
    }

    @Operation(summary = "Üyelik fiyat tablosunu güncelle (ADMIN)")
    @PutMapping("/admin/config/plan-pricing")
    public PlanPricingService.PricingAdminView updatePlanPricing(@Valid @RequestBody PlanPricingRequest request) {
        return planPricingService.update(new PlanPricingService.PricingTable(
                request.proMonthly(), request.proYearly(),
                request.premiumMonthly(), request.premiumYearly(), request.currency()));
    }

    /** Boş değer = kaydı sil, ortam yedeğine dön. */
    public record MapboxTokenRequest(@Size(max = 500) String token) {
    }

    /**
     * Dört hücre birlikte gönderilir.
     *
     * <p>⚠️ Tek tek güncelleme ucu <b>bilinçli olarak yok</b>: yıllığın aylıktan
     * düşük olması kuralı ancak dördü bir arada görülünce doğrulanabilir.
     * Değerler <b>aylık eşdeğerdir</b>; yıllıkta 12 katı tahsil edilir.
     */
    public record PlanPricingRequest(
            @NotNull(message = "Pro aylık ücreti zorunludur")
            @DecimalMin(value = "0.01", message = "Ücret sıfırdan büyük olmalı")
            BigDecimal proMonthly,
            @NotNull(message = "Pro yıllık ücreti zorunludur")
            @DecimalMin(value = "0.01", message = "Ücret sıfırdan büyük olmalı")
            BigDecimal proYearly,
            @NotNull(message = "Premium aylık ücreti zorunludur")
            @DecimalMin(value = "0.01", message = "Ücret sıfırdan büyük olmalı")
            BigDecimal premiumMonthly,
            @NotNull(message = "Premium yıllık ücreti zorunludur")
            @DecimalMin(value = "0.01", message = "Ücret sıfırdan büyük olmalı")
            BigDecimal premiumYearly,
            @NotBlank(message = "Para birimi zorunludur")
            String currency) {
    }
}
