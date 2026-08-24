package com.waydee.territory.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * <b>Mağaza kategorisi</b> (V52, 24 Ağu 2026).
 *
 * <p>🔴 <b>Neden enum değil tablo:</b> {@link StoreMarkerStyle} bir enumdur ve
 * öyle kalmalıdır — orada her değer <b>kodda ayrı bir çizim yolu</b> demektir.
 * Kategori bunun tersidir: yeni bir kategori hiçbir kod yolu açmaz, yalnız bir
 * satırdır. Kullanıcı isteği zaten *"eklenip çıkarılabilir olsun"* — enum
 * olsaydı her yeni kategori bir migration + yeniden dağıtım olurdu.
 *
 * <p>⚠️ <b>{@code code} değişmez.</b> Çeviri sözlüğünün anahtarıdır
 * ({@code storeCategory.FASHION}); değişirse beş dildeki karşılığı birden
 * kopar. Yönetici {@code name}, {@code icon}, {@code color} ve sırayı
 * değiştirebilir — kodu değiştiremez.
 *
 * <p>⚠️ <b>Silme yok, {@link #active} var.</b> Silmek, o kategoriyi seçmiş
 * mağazaların referansını da götürürdü. Pasif kategori seçim listelerinde
 * çıkmaz ama <b>zaten seçmiş</b> mağazada durmaya devam eder.
 */
@Entity
@Table(name = "store_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreCategory extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** Sabit, insan okunur çeviri anahtarı. Oluşturulduktan sonra değişmez. */
    @Column(name = "code", nullable = false, length = 32, unique = true, updatable = false)
    private String code;

    /**
     * Sözlükte karşılığı olmayan kategorilerin gösterilen adı.
     *
     * <p>⚠️ Çekirdek kategorilerde de dolu, ama istemci önce sözlüğe bakar:
     * {@code storeCategory.<code>} varsa o, yoksa burası. Böylece yöneticinin
     * sonradan eklediği kategori de bir ada sahip olur.
     */
    @Column(name = "name", nullable = false, length = 60)
    @Setter
    private String name;

    /** Phosphor ikon adı (ör. {@code TShirt}). İstemcideki beyaz listede yoksa yedek çizilir. */
    @Column(name = "icon", nullable = false, length = 48)
    @Setter
    private String icon;

    /** Şeritteki ikon rengi {@code #RRGGBB}. */
    @Column(name = "color", nullable = false, length = 9)
    @Setter
    private String color;

    @Column(name = "sort_order", nullable = false)
    @Setter
    private int sortOrder;

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active = true;

    public StoreCategory(String code, String name, String icon, String color, int sortOrder) {
        this.code = code;
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.sortOrder = sortOrder;
        this.active = true;
    }
}
