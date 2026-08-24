package com.waydee.social.infrastructure;

import com.waydee.social.domain.PostTagDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PostTagStatsRepository extends JpaRepository<PostTagDailyStat, PostTagDailyStat.Key> {

    /**
     * Gösterim/tıklama sayacını <b>atomik</b> artırır.
     *
     * <p>🔴 "Oku → +1 → yaz" deseni KULLANILMAZ. Vault'ta anket oyu sayacıyla
     * bire bir aynı ders var: eşzamanlı iki istek aynı değeri okur, ikisi de
     * aynı sayıyı yazar ve <b>bir artış kaybolur</b>. {@code ON CONFLICT … DO
     * UPDATE SET x = x + 1} tek ifadede, veritabanı kilidiyle çözer.
     *
     * <p>⚠️ Satır yoksa yaratır (UPSERT) — istemci "bugün ilk mi?" diye
     * sormak zorunda kalmasın.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO post_tag_daily_stats (tag_id, post_id, day, impressions, clicks)
            VALUES (:tagId, :postId, :day, :impressions, :clicks)
            ON CONFLICT (tag_id, day) DO UPDATE
               SET impressions = post_tag_daily_stats.impressions + EXCLUDED.impressions,
                   clicks      = post_tag_daily_stats.clicks      + EXCLUDED.clicks
            """, nativeQuery = true)
    void record(@Param("tagId") UUID tagId,
                @Param("postId") UUID postId,
                @Param("day") LocalDate day,
                @Param("impressions") int impressions,
                @Param("clicks") int clicks);

    /** Rapor: bir gönderinin tüm etiketleri, verilen günden bugüne. */
    @Query("""
            SELECT s FROM PostTagDailyStat s
             WHERE s.postId = :postId AND s.id.day >= :since
             ORDER BY s.id.day ASC
            """)
    List<PostTagDailyStat> forPostSince(@Param("postId") UUID postId, @Param("since") LocalDate since);

    /** Rapor: kullanıcının TÜM etiketleri (gönderi ayrımı olmadan toplam). */
    @Query(value = """
            SELECT s.tag_id, SUM(s.impressions), SUM(s.clicks)
              FROM post_tag_daily_stats s
              JOIN post_tags t ON t.id = s.tag_id
              JOIN posts p     ON p.id = t.post_id
             WHERE p.author_id = :authorId AND s.day >= :since
             GROUP BY s.tag_id
            """, nativeQuery = true)
    List<Object[]> totalsByAuthorSince(@Param("authorId") UUID authorId, @Param("since") LocalDate since);
}
