package com.waydee.social.infrastructure;

import com.waydee.social.domain.Story;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    /**
     * Görüntüleyene GÖRÜNÜR aktif hikayeler: kendi + herkese açık hesaplar +
     * takip ettiği gizli hesaplar. Sınırlı (Pageable) — global taramaya izin yok.
     */
    @Query("""
            select s from Story s join fetch s.author a
            where s.expiresAt > :now
              and (a.id = :viewerId
                   or a.privateAccount = false
                   or exists (select f from Follow f
                              where f.followerId = :viewerId and f.followeeId = a.id
                                and f.status = com.waydee.identity.domain.FollowStatus.ACCEPTED))
            order by s.createdAt asc
            """)
    List<Story> findActiveVisible(@Param("now") Instant now, @Param("viewerId") UUID viewerId, Pageable pageable);

    /**
     * <b>Takip ettiklerimin</b> aktif hikayeleri (+ kendi hikayem).
     *
     * <p>⚠️ {@link #findActiveVisible}'dan farkı: orası <b>tüm açık hesapları</b>
     * getirir (keşif şeridi). Ana sayfadaki şerit ise takip ilişkisine dayanır —
     * kullanıcı birini takip ettiği için hikayesini görür. İkisi ayrı sorgu
     * çünkü ayrı iki üründür; tek sorguya bayrak koymak, birinin değişiminin
     * diğerini sessizce bozmasına yol açardı.
     */
    @Query("""
            select s from Story s join fetch s.author a
            where s.expiresAt > :now
              and (a.id = :viewerId
                   or exists (select f from Follow f
                              where f.followerId = :viewerId and f.followeeId = a.id
                                and f.status = com.waydee.identity.domain.FollowStatus.ACCEPTED))
            order by s.createdAt asc
            """)
    List<Story> findActiveFollowing(@Param("now") Instant now, @Param("viewerId") UUID viewerId, Pageable pageable);

    /** Bir kullanıcının aktif hikayeleri. */
    @Query("select s from Story s join fetch s.author where s.author.id = :authorId and s.expiresAt > :now order by s.createdAt asc")
    List<Story> findActiveByAuthor(@Param("authorId") UUID authorId, @Param("now") Instant now);

    /** Bir bölgede (harita profilinde) yayınlanmış aktif hikayeler. */
    @Query("select s from Story s join fetch s.author where s.territoryId = :territoryId and s.expiresAt > :now order by s.createdAt asc")
    List<Story> findActiveByTerritory(@Param("territoryId") UUID territoryId, @Param("now") Instant now);
}
