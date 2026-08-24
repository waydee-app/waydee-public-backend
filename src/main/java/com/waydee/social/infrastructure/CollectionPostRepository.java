package com.waydee.social.infrastructure;

import com.waydee.social.domain.CollectionPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CollectionPostRepository
        extends JpaRepository<CollectionPost, CollectionPost.CollectionPostId> {

    /*
     * ⚠️ Koleksiyonun GÖNDERİLERİNİ döndüren sorgu bilerek burada DEĞİL,
     * `PostRepository`'de duruyor: `@EntityGraph` repository'nin KÖK tipine
     * (burada `CollectionPost`) uygulanır, sorgunun döndürdüğü tipe değil.
     * Burada yazıldığında Hibernate `CollectionPost` üstünde `media` alanını
     * arayıp `IllegalArgumentException` fırlatıyordu (ölçüldü: 500).
     */

    long countByIdCollectionId(UUID collectionId);

    boolean existsByIdCollectionIdAndIdPostId(UUID collectionId, UUID postId);
}
