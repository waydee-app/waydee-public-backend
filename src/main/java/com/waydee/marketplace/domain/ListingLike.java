package com.waydee.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stant beğenisi — bileşik anahtar (listing + user), tek beğeni garantisi DB'de. */
@Entity
@Table(name = "marketplace_listing_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListingLike {

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ListingLike(UUID listingId, UUID userId) {
        this.id = new Key(listingId, userId);
        this.createdAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "listing_id", nullable = false)
        private UUID listingId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public Key(UUID listingId, UUID userId) {
            this.listingId = listingId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key key)) {
                return false;
            }
            return Objects.equals(listingId, key.listingId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(listingId, userId);
        }
    }
}
