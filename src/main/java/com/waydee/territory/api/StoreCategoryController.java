package com.waydee.territory.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.territory.api.dto.StoreCategoryDtos.ChooseCategoryRequest;
import com.waydee.territory.api.dto.StoreCategoryDtos.StoreCategoryResponse;
import com.waydee.territory.application.StoreCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kategori listesi (V52).
 *
 * <p>⚠️ Uç <b>oturumlu</b> tarafta. Vitrin haritasının da kategorilere ihtiyacı
 * var ama o {@code /public/**} altından okur — bkz.
 * {@code PublicStoreCategoryController}. İkisi ayrı çünkü {@code /public/**}
 * <b>yalnız GET</b> için açıktır ve tek bir uç iki güvenlik kuralına aynı anda
 * tabi olamaz.
 */
@Tag(name = "Store Categories", description = "Mağaza kategorileri")
@RestController
@RequestMapping("/api/v1/store-categories")
@RequiredArgsConstructor
public class StoreCategoryController {

    private final StoreCategoryService service;

    @Operation(summary = "Seçilebilir kategoriler")
    @GetMapping
    public List<StoreCategoryResponse> list() {
        return service.listActive();
    }

    /**
     * <b>Kayıt sonrası popup'ın cevabı ve ayarlardaki değişiklik</b> (V52).
     *
     * <p>⚠️ {@code PUT}, {@code PATCH} değil: gönderilen gövde alanın
     * <b>tamamıdır</b> ve {@code categoryId: null} burada "dokunma" değil
     * <b>"geç"</b> demektir. PATCH sözleşmesinde null "dokunma"dır ve bu uç
     * tam olarak onun tersine ihtiyaç duyuyor.
     */
    @Operation(summary = "Mağaza alanımı seç (popup / ayarlar)")
    @PutMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void choose(@AuthenticationPrincipal AuthenticatedUser principal,
                       @Valid @RequestBody ChooseCategoryRequest request) {
        service.chooseForUser(principal.id(), request.categoryId());
    }
}
