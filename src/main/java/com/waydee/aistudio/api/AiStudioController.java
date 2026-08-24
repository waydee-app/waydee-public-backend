package com.waydee.aistudio.api;

import com.waydee.aistudio.api.dto.AiStudioDtos.FashionModelRequest;
import com.waydee.aistudio.api.dto.AiStudioDtos.GenerationResponse;
import com.waydee.aistudio.api.dto.AiStudioDtos.QuoteResponse;
import com.waydee.aistudio.application.AiStudioService;
import com.waydee.aistudio.application.CreditCost;
import com.waydee.aistudio.application.FashionOptions;
import com.waydee.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Yapay zekâ stüdyosu</b> (V45).
 *
 * <p>🔒 Her uç oturum ister ve kimlik <b>oturumdan</b> alınır; istekte kullanıcı
 * kimliği kabul edilmez. Plan kapısı ve kredi düşümü servistedir — istemcinin
 * düğmeyi gizlemesi güvenlik sayılmaz (projenin diğer kapılarıyla aynı ilke).
 */
@Tag(name = "AI Studio", description = "Yapay zekâ görsel üretimi")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiStudioController {

    private final AiStudioService aiStudioService;

    /**
     * Açılır listelerin <b>kodları</b>.
     *
     * <p>🔴 Etiket <b>döndürülmez</b>, yalnız kod. Vault kuralı (83. tur):
     * <i>"sunucudan gelen {@code *Label} alanlarını çok dilli arayüzde basma"</i>
     * — çeviri istemcide, koda göre yapılır. Böylece beş dil de aynı listeyi
     * kendi dilinde gösterir ve sunucu tek bir dile mahkûm olmaz.
     *
     * <p>⚠️ Kodların <b>kaynağı</b> sunucudur: istemci listeyi kopyalasaydı
     * ikisi zamanla ayrışır ve kullanıcı sunucunun reddedeceği bir seçenek
     * görebilirdi.
     */
    @Operation(summary = "Manken ayar seçenekleri (kod listesi)")
    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of(
                "catalogue", FashionOptions.catalogue(),
                "maxProducts", CreditCost.MAX_PRODUCTS,
                "costStandard", CreditCost.BASE_STANDARD,
                "costHigh", CreditCost.BASE_HIGH,
                "costExtraProduct", CreditCost.EXTRA_PRODUCT);
    }

    /**
     * Maliyet önizlemesi — "Oluştur"un altındaki <b>… kredi</b> yazısı.
     *
     * <p>🔴 Ayrı bir uç olması bilinçli: istemci formülü kopyalamaz, <b>sorar</b>.
     * İki formül olsaydı biri diğerinden sapar ve ekranda yazan rakam ile düşen
     * rakam farklı olurdu.
     */
    @Operation(summary = "Bu ayarlar kaç krediye mal olur")
    @GetMapping("/quote")
    public QuoteResponse quote(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(defaultValue = "false") boolean highQuality,
                               @RequestParam(defaultValue = "1") int productCount) {
        return aiStudioService.quote(user.id(), highQuality, productCount);
    }

    /**
     * Üretimi başlatır ve <b>hemen</b> {@code QUEUED} durumuyla döner.
     *
     * <p>⚠️ Senkron beklemek 6–40 saniye boyunca bir istek thread'ini bloke
     * ederdi. İstemci sonucu {@code GET /ai/generations/{id}} ile yoklar.
     */
    @Operation(summary = "Fast Model üretimi başlat")
    @PostMapping("/fashion-model")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GenerationResponse createFashionModel(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody FashionModelRequest request) {
        return aiStudioService.createFashionModel(user.id(), request);
    }

    @Operation(summary = "Üretimlerim (galeri)")
    @GetMapping("/generations")
    public List<GenerationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(defaultValue = "30") int limit) {
        return aiStudioService.list(user.id(), limit);
    }

    @Operation(summary = "Tek üretim (durum yoklama)")
    @GetMapping("/generations/{id}")
    public GenerationResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable UUID id) {
        return aiStudioService.get(user.id(), id);
    }
}
