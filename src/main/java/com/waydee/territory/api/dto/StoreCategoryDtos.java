package com.waydee.territory.api.dto;

import com.waydee.territory.domain.StoreCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Mağaza kategorisi uçlarının veri sözleşmeleri (V52). */
public final class StoreCategoryDtos {

    private StoreCategoryDtos() {
    }

    /**
     * Kategori — istemcinin gördüğü hâli.
     *
     * <p>⚠️ {@code name} sunucunun TÜRKÇE fallback'idir, gösterilecek ad
     * <b>değildir</b>. İstemci önce {@code storeCategory.<code>} sözlük
     * anahtarına bakar; yoksa buraya düşer. Sunucudan beş dil göndermek,
     * sözlüğü ikiye bölmek olurdu.
     */
    public record StoreCategoryResponse(
            UUID id,
            String code,
            String name,
            String icon,
            String color,
            int sortOrder,
            boolean active
    ) {
        public static StoreCategoryResponse from(StoreCategory c) {
            return new StoreCategoryResponse(c.getId(), c.getCode(), c.getName(),
                    c.getIcon(), c.getColor(), c.getSortOrder(), c.isActive());
        }
    }

    /** Kullanıcının kayıt sonrası verdiği cevap. {@code categoryId} null → "geç". */
    public record ChooseCategoryRequest(UUID categoryId) {
    }

    // ------------------------------------------------------------------ admin

    public record AdminCreateCategoryRequest(
            @NotBlank @Size(max = 32)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Kod BÜYÜK harf, rakam ve _ olmalı")
            String code,
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 48) String icon,
            @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Renk #RRGGBB olmalı") String color,
            @Min(0) @Max(999) Integer sortOrder
    ) {
    }

    /**
     * Güncelleme — <b>kod yok</b>.
     *
     * <p>🔴 Kod çeviri anahtarıdır; değişmesi beş dildeki karşılığını birden
     * koparır ve o an hiçbir hata vermez, yalnız adlar bir gün İngilizceye
     * döner. Bu yüzden alan DTO'da hiç yok — "boş bırakırsan dokunmam"
     * bile değil, gönderilemez.
     */
    public record AdminUpdateCategoryRequest(
            @Size(max = 60) String name,
            @Size(max = 48) String icon,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Renk #RRGGBB olmalı") String color,
            @Min(0) @Max(999) Integer sortOrder,
            Boolean active
    ) {
    }
}
