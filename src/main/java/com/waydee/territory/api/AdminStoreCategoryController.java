package com.waydee.territory.api;

import com.waydee.territory.api.dto.StoreCategoryDtos.AdminCreateCategoryRequest;
import com.waydee.territory.api.dto.StoreCategoryDtos.AdminUpdateCategoryRequest;
import com.waydee.territory.api.dto.StoreCategoryDtos.StoreCategoryResponse;
import com.waydee.territory.application.StoreCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

/**
 * Kategori yönetimi (yalnız ADMIN — {@code /api/v1/admin/**} rol kapılı).
 *
 * <p>🔴 <b>DELETE ucu YOK ve bilerek yok.</b> Kategori silmek, onu seçmiş
 * mağazaların referansını da götürürdü (FK {@code ON DELETE RESTRICT} zaten
 * izin vermez). "Çıkarma" işlemi {@code active=false}'tur: kategori seçim
 * listelerinden düşer, zaten seçmiş mağazalarda durmaya devam eder.
 */
@Tag(name = "Admin Store Categories", description = "Mağaza kategorisi yönetimi")
@RestController
@RequestMapping("/api/v1/admin/store-categories")
@RequiredArgsConstructor
public class AdminStoreCategoryController {

    private final StoreCategoryService service;

    @Operation(summary = "Tüm kategoriler (pasifler dahil)")
    @GetMapping
    public List<StoreCategoryResponse> list() {
        return service.listAll();
    }

    @Operation(summary = "Kategori ekle")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreCategoryResponse create(@Valid @RequestBody AdminCreateCategoryRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Kategoriyi güncelle / pasife al")
    @PatchMapping("/{id}")
    public StoreCategoryResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody AdminUpdateCategoryRequest request) {
        return service.update(id, request);
    }
}
