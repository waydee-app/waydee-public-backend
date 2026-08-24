package com.waydee.marketplace.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 3B mağazanın <b>rafındaki tek bir ürün</b>.
 *
 * <p>İki kaynaktan beslenir ({@link StoreProductSource}); tutarlılık
 * veritabanında {@code ck_store_product_post} ile zorlanır — servis katmanındaki
 * bir {@code if}, toplu içe aktarma ya da elle SQL ile atlanabilirdi.
 */
@Entity
@Table(name = "marketplace_store_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreProduct extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private StoreProductSource source;

    /** Yalnız {@link StoreProductSource#POST} iken dolu. */
    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "title", nullable = false, length = 140)
    @Setter
    private String title;

    @Column(name = "description", length = 500)
    @Setter
    private String description;

    @Column(name = "price", precision = 12, scale = 2)
    @Setter
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    @Setter
    private String currency;

    @Column(name = "product_url", length = 500)
    @Setter
    private String productUrl;

    /** Yalnız {@link StoreProductSource#CUSTOM} iken anlamlı. */
    @Column(name = "image_media_id")
    @Setter
    private UUID imageMediaId;

    /**
     * Raf sırası. ⚠️ Beğeni/tarih ile DEĞİL, elle belirlenir — vault kuralı:
     * sıra veriyle değişirse ziyaretçi dün gördüğü rafı bugün bulamaz.
     */
    @Column(name = "position", nullable = false)
    @Setter
    private int position;

    @Column(name = "visible", nullable = false)
    @Setter
    private boolean visible = true;

    /** Profildeki bir gönderiden raf ürünü. */
    public static StoreProduct fromPost(UUID listingId, UUID postId, String title, int position) {
        StoreProduct p = new StoreProduct();
        p.listingId = listingId;
        p.source = StoreProductSource.POST;
        p.postId = postId;
        p.title = title;
        p.position = position;
        return p;
    }

    /** Yalnız mağazaya özel, sıfırdan ürün. */
    public static StoreProduct custom(UUID listingId, String title, int position) {
        StoreProduct p = new StoreProduct();
        p.listingId = listingId;
        p.source = StoreProductSource.CUSTOM;
        p.title = title;
        p.position = position;
        return p;
    }
}
