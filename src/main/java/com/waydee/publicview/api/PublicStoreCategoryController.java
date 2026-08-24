package com.waydee.publicview.api;

import com.waydee.territory.api.dto.StoreCategoryDtos.StoreCategoryResponse;
import com.waydee.territory.application.StoreCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vitrin haritasının kategori şeridi (V52) — kimliksiz okuma.
 *
 * <p>⚠️ {@code /public/**} <b>yalnız GET</b> için açıktır (91. turda ölçüldü:
 * anonim POST 401 alıyor ve hata sessiz kalıyordu). Burada yalnız GET var.
 */
@Tag(name = "Public", description = "Kimliksiz vitrin uçları")
@RestController
@RequestMapping("/api/v1/public/store-categories")
@RequiredArgsConstructor
public class PublicStoreCategoryController {

    private final StoreCategoryService service;

    @Operation(summary = "Seçilebilir kategoriler (kimliksiz)")
    @GetMapping
    public List<StoreCategoryResponse> list() {
        return service.listActive();
    }
}
