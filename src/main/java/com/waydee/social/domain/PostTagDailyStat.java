package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bir etiketin <b>bir güne ait</b> gösterim/tıklama sayısı.
 *
 * <p>Ham olay saklanmaz — gerekçe {@code V44} migration başlığında: gösterim
 * olayı tıklamadan kat kat sık üretilir ve rapor zaten gün çözünürlüğünde.
 *
 * <p>⚠️ Bu entity yalnız <b>okuma</b> için. Artırma, yarış koşulunu önlemek
 * için native {@code UPSERT} ile yapılır ({@code PostTagStatsRepository.record}) —
 * "oku, +1 yap, yaz" deseni iki eşzamanlı istekte sayı kaybederdi.
 */
@Entity
@Table(name = "post_tag_daily_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostTagDailyStat {

    @EmbeddedId
    private Key id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "impressions", nullable = false)
    private int impressions;

    @Column(name = "clicks", nullable = false)
    private int clicks;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "tag_id", nullable = false)
        private UUID tagId;

        /** UTC gün — yerelleştirme arayüzde yapılır. */
        @Column(name = "day", nullable = false)
        private LocalDate day;

        public Key(UUID tagId, LocalDate day) {
            this.tagId = tagId;
            this.day = day;
        }
    }
}
