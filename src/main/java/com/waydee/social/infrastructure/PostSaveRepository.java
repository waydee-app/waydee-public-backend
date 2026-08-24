package com.waydee.social.infrastructure;

import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostSaveRepository extends JpaRepository<PostSave, PostSave.PostSaveId> {

    /** Akıştaki gönderilerin hangilerini kaydetmişim — tek sorguda. */
    @Query("SELECT s.id.postId FROM PostSave s WHERE s.id.userId = :userId AND s.id.postId IN :postIds")
    List<UUID> findSavedPostIds(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);

    /**
     * <b>Kaydettiklerim</b> — en yeniden eskiye.
     *
     * <p>⚠️ Sıralama gönderinin tarihine göre DEĞİL, <b>kaydetme anına</b> göre:
     * kullanıcı dün kaydettiği eski bir gönderiyi listenin başında bekler.
     * (Tablodaki {@code idx_post_saves_user} indeksi tam bunun için var.)
     *
     * <p>⚠️ Silinmiş gönderiler süzülür — kaydedilmiş olmaları onları geri
     * getirmez.
     */
    @Query("""
            SELECT p FROM PostSave s
            JOIN Post p ON p.id = s.id.postId
            WHERE s.id.userId = :userId AND p.deletedAt IS NULL
            ORDER BY s.savedAt DESC
            """)
    Page<Post> findSavedPosts(@Param("userId") UUID userId, Pageable pageable);
}
