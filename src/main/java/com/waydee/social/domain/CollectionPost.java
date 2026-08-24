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
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Bir gönderinin bir koleksiyondaki üyeliği (V29 {@code collection_posts}).
 *
 * <p>Tablo V29'da açılmıştı ama JPA eşlemesi yoktu — koleksiyona gönderi
 * eklemek bu yüzden hiç mümkün değildi. Bileşik anahtar deseni
 * {@link PostLike} ile aynıdır (ayrı bir yüzey icat etmemek için).
 *
 * <p>⚠️ {@code position} kullanıcının ekleme sırasıdır; koleksiyon ızgarası
 * bu sıraya göre çizilir, yoksa her açılışta farklı sıralanırdı.
 */
@Entity
@Table(name = "collection_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionPost {

    @EmbeddedId
    private CollectionPostId id;

    @Setter
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public CollectionPost(UUID collectionId, UUID postId, int position) {
        this.id = new CollectionPostId(collectionId, postId);
        this.position = position;
        this.addedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class CollectionPostId implements Serializable {

        @Column(name = "collection_id")
        private UUID collectionId;

        @Column(name = "post_id")
        private UUID postId;

        public CollectionPostId(UUID collectionId, UUID postId) {
            this.collectionId = collectionId;
            this.postId = postId;
        }
    }
}
