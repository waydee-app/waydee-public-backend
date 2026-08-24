package com.waydee.social.domain;

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
import java.time.Instant;
import java.util.UUID;

/**
 * Hesaplanmış trend sırası — "yükselişte" duyuru şeridinin kaynağı.
 *
 * Skor periyodik hesaplanır ve buraya YAZILIR; okuma ucu yalnız sıralar.
 * Her istekte beş ayrı GROUP BY koşturmak, şerit sürekli açık durduğu için
 * kabul edilemezdi.
 */
@Entity
@Table(name = "trending_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrendingEntry {

    @Id
    @UuidGenerator
    private UUID id;

    /** TERRITORY | USER */
    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "score", nullable = false, precision = 12, scale = 4)
    @Setter
    private BigDecimal score;

    /** Önceki turdaki sıra; null → listeye YENİ girdi. */
    @Column(name = "previous_rank")
    @Setter
    private Integer previousRank;

    @Column(name = "rank_position", nullable = false)
    @Setter
    private int rank;

    @Column(name = "views_7d", nullable = false)
    @Setter
    private int views7d;

    @Column(name = "likes_7d", nullable = false)
    @Setter
    private int likes7d;

    @Column(name = "saves_7d", nullable = false)
    @Setter
    private int saves7d;

    @Column(name = "posts_7d", nullable = false)
    @Setter
    private int posts7d;

    @Column(name = "followers_7d", nullable = false)
    @Setter
    private int followers7d;

    @Column(name = "computed_at", nullable = false)
    @Setter
    private Instant computedAt = Instant.now();

    public TrendingEntry(String subjectType, UUID subjectId) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.score = BigDecimal.ZERO;
        this.rank = 0;
    }

    /** Sıra kaç basamak yükseldi (negatif = düştü, null = yeni). */
    public Integer climb() {
        return previousRank == null ? null : previousRank - rank;
    }
}
