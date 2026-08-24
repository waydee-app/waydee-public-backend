package com.waydee.marketplace.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Kabul edilen stant sahibinin <b>3B mağazasını</b> özelleştirdiği ayarlar.
 *
 * <h2>Neden {@link MarketplaceListing}'in içinde değil?</h2>
 * <p>Stant bir <b>başvuru</b> kaydıdır: admin onaylar ve içerik büyük ölçüde
 * donar. Stüdyo ise sahibinin <b>istediği zaman</b> oynadığı sunum verisidir
 * (ışık rengi, müzik, televizyon). İkisini aynı satıra koymak, her renk
 * denemesinde başvurunun denetim alanlarını ({@code updated_by}, {@code version})
 * kirletirdi ve "başvuru değişti mi?" sorusu cevapsız kalırdı.
 *
 * <p>⚠️ Kimlik <b>paylaşılır</b>: birincil anahtar stantın kimliğidir (1:1).
 * Ayrı bir {@code id} üretmek, "bir stantın iki ayarı olabilir mi?" sorusunu
 * veritabanı seviyesinde açık bırakırdı.
 */
@Entity
@Table(name = "marketplace_store_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreSettings extends AuditableEntity {

    /** Stantın kimliği — ayrı bir anahtar YOK (1:1). */
    @Id
    @Column(name = "listing_id")
    private UUID listingId;

    /** Tabelada yazan ad. Boşsa {@code listing.title} kullanılır. */
    @Column(name = "display_name", length = 90)
    @Setter
    private String displayName;

    // ------------------------------------------------------------ televizyon
    @Column(name = "video_media_id")
    @Setter
    private UUID videoMediaId;

    @Column(name = "tv_enabled", nullable = false)
    @Setter
    private boolean tvEnabled = true;

    /**
     * ⚠️ Varsayılan <b>sessiz</b>. Caddede 40 mağazanın sesi aynı anda açık
     * olsaydı sahne kullanılamaz olurdu; ziyaretçi istediği dükkânın sesini
     * kendisi açar.
     */
    @Column(name = "tv_muted", nullable = false)
    @Setter
    private boolean tvMuted = true;

    // ------------------------------------------------------------ müzik
    @Column(name = "music_media_id")
    @Setter
    private UUID musicMediaId;

    @Column(name = "music_enabled", nullable = false)
    @Setter
    private boolean musicEnabled = false;

    /** 0–100; istemci 0..1'e böler. */
    @Column(name = "music_volume", nullable = false)
    @Setter
    private int musicVolume = 35;

    // ------------------------------------------------------------ görsel
    @Column(name = "light_color", nullable = false, length = 9)
    @Setter
    private String lightColor = "#ffd9a0";

    /** 0–200 (yüzde). 100 = varsayılan şiddet. */
    @Column(name = "light_intensity", nullable = false)
    @Setter
    private int lightIntensity = 100;

    @Column(name = "accent_color", nullable = false, length = 9)
    @Setter
    private String accentColor = "#83bf6e";

    @Column(name = "facade_color", nullable = false, length = 9)
    @Setter
    private String facadeColor = "#111315";

    public StoreSettings(UUID listingId) {
        this.listingId = listingId;
    }
}
