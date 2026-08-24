package com.waydee.geo.api;

import com.waydee.common.error.ApiException;
import com.waydee.geo.domain.GlobalPricing;
import com.waydee.geo.infrastructure.GlobalPricingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Küresel taban fiyat — <b>tüm dünyada</b> geçerli, haritada çizilmeyen katman.
 *
 * <p>⚠️ Neden var: fiyat şimdiye kadar yalnız çizilen poligonlardan ve idari
 * hiyerarşiden çözülüyordu; ikisi de yoksa hiçbir yer satılamıyordu. Bu katman
 * tanımlıysa dünyanın her noktasında bir taban fiyat vardır.
 *
 * <p>⚠️ Üst üste gelen katmanlarda <b>en pahalı kazanır</b> — küresel taban da
 * bir adaydır, üstüne çizilen pahalı bir bölge onu geçer.
 */
@Tag(name = "Admin Pricing", description = "Küresel taban fiyat")
@RestController
@RequestMapping("/api/v1/admin/pricing/global")
@RequiredArgsConstructor
public class AdminGlobalPricingController {

    private final GlobalPricingRepository repository;

    @Operation(summary = "Küresel taban fiyatı oku")
    @GetMapping
    @Transactional(readOnly = true)
    public GlobalPricingView get() {
        return GlobalPricingView.from(load());
    }

    @Operation(summary = "Küresel taban fiyatı güncelle")
    @PutMapping
    @Transactional
    public GlobalPricingView update(@Valid @RequestBody GlobalPricingRequest request) {
        GlobalPricing pricing = load();
        pricing.setPrice(request.price());
        pricing.setUnit(request.unit());
        pricing.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "TRY" : request.currency().toUpperCase());
        pricing.setActive(Boolean.TRUE.equals(request.active()));
        return GlobalPricingView.from(repository.save(pricing));
    }

    /**
     * Tek satır garanti altında: migration onu ekliyor, yine de yoksa
     * (elle silinmiş bir DB) sessizce kapalı bir satır üretilir — yönetim
     * ekranı 500 ile karşılaşmamalı.
     */
    private GlobalPricing load() {
        return repository.findById(GlobalPricing.SINGLETON_ID)
                .orElseThrow(() -> ApiException.notFound("Küresel fiyat kaydı bulunamadı"));
    }

    public record GlobalPricingRequest(
            @NotNull @DecimalMin(value = "0", message = "Fiyat negatif olamaz") BigDecimal price,
            @NotNull @Pattern(regexp = "M2|KM2", message = "Birim M2 ya da KM2 olmalı") String unit,
            String currency,
            Boolean active
    ) {
    }

    public record GlobalPricingView(
            BigDecimal price,
            String unit,
            String currency,
            boolean active,
            /** Bilgi amaçlı: girilen değerin km² karşılığı (karşılaştırma bu birimde yapılır). */
            BigDecimal pricePerKm2
    ) {
        static GlobalPricingView from(GlobalPricing g) {
            return new GlobalPricingView(g.getPrice(), g.getUnit(), g.getCurrency(), g.isActive(),
                    g.pricePerKm2());
        }
    }
}
