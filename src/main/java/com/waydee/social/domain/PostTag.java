package com.waydee.social.domain;

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

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fotoğrafın <b>üstündeki</b> bir ürün etiketi.
 *
 * <p>Kullanıcı görsele tıklar, oraya bir nokta bırakır ve o nokta bir ürüne
 * gider. Etiket bu yüzden "gönderiye bağlı bir metin" değil, <b>konumu olan</b>
 * bir nesnedir.
 *
 * <p>⚠️ <b>Konum YÜZDE olarak saklanır (0–1), piksel değil.</b> Aynı fotoğraf
 * telefonda ~340px, masaüstünde ~620px genişlikte çizilir; piksel saklansaydı
 * etiket her ekranda başka bir yere düşerdi. Yüzde, görselin kendi koordinat
 * uzayıdır ve ölçekten bağımsızdır.
 */
@Entity
@Table(name = "post_tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTag extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    /** Görselin sol kenarından yatay oran (0–1). */
    @Column(name = "x", nullable = false, precision = 6, scale = 5)
    @Setter
    private BigDecimal x;

    /** Görselin üst kenarından dikey oran (0–1). */
    @Column(name = "y", nullable = false, precision = 6, scale = 5)
    @Setter
    private BigDecimal y;

    @Column(name = "product_url", nullable = false, length = 500)
    @Setter
    private String productUrl;

    @Column(name = "product_name", length = 140)
    @Setter
    private String productName;

    /** Opsiyonel: "Auto Fetch Data" ile hedef siteden çekilebilir. */
    @Column(name = "price", precision = 12, scale = 2)
    @Setter
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    @Setter
    private String currency;

    @Column(name = "image_media_id")
    @Setter
    private UUID imageMediaId;

    @Column(name = "position", nullable = false)
    @Setter
    private int position;

    @Column(name = "click_count", nullable = false)
    private int clickCount;

    public PostTag(UUID postId, BigDecimal x, BigDecimal y, String productUrl, int position) {
        this.postId = postId;
        this.x = x;
        this.y = y;
        this.productUrl = productUrl;
        this.position = position;
    }
}
