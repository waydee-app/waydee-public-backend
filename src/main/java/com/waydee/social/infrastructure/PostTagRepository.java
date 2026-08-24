package com.waydee.social.infrastructure;

import com.waydee.social.domain.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostTagRepository extends JpaRepository<PostTag, UUID> {

    List<PostTag> findByPostIdOrderByPositionAsc(UUID postId);

    /**
     * Bir gönderi kümesinin etiketleri — <b>tek sorguda</b>.
     *
     * <p>⚠️ Izgara ekranı 20 gönderi çiziyor; gönderi başına ayrı sorgu
     * klasik N+1 olurdu.
     */
    List<PostTag> findByPostIdInOrderByPositionAsc(List<UUID> postIds);

    long countByPostId(UUID postId);

    void deleteByPostId(UUID postId);

    /** Sahibin toplam etiket sayısı (Analytics "Total Tags"). */
    @Query("""
            SELECT COUNT(t) FROM PostTag t
            WHERE t.postId IN (SELECT p.id FROM Post p WHERE p.author.id = :ownerId AND p.deletedAt IS NULL)
            """)
    long countByOwner(@Param("ownerId") UUID ownerId);

    /** Etiket tıklaması — atomik; yarış koşulu yok. */
    @Modifying
    @Query("UPDATE PostTag t SET t.clickCount = t.clickCount + 1 WHERE t.id = :id")
    int recordClick(@Param("id") UUID id);
}
