package com.waydee.social.infrastructure;

import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLike.PostLikeId> {

    @Query("SELECT l.id.postId FROM PostLike l WHERE l.id.userId = :userId AND l.id.postId IN :postIds")
    List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);

    /**
     * <b>Begendiklerim</b> — en yeniden eskiye.
     *
     * <p>⚠️ Siralama gonderinin tarihine gore DEGIL, <b>begenme anina</b>
     * gore: kullanici dun begendigi eski bir gonderiyi listenin basinda bekler.
     *
     * <p>⚠️ Silinmis gonderiler suzulur — begenilmis olmalari onlari geri
     * getirmez.
     */
    @Query("""
            SELECT p FROM PostLike l
            JOIN Post p ON p.id = l.id.postId
            WHERE l.id.userId = :userId AND p.deletedAt IS NULL
            ORDER BY l.createdAt DESC
            """)
    Page<Post> findLikedPosts(@Param("userId") UUID userId, Pageable pageable);
}
