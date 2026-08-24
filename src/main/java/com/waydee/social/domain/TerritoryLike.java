package com.waydee.social.domain;

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

/** Dairenin (bölgenin) kendisine verilen beğeni — gönderi beğenisinden ayrıdır. */
@Entity
@Table(name = "territory_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryLike {

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TerritoryLike(UUID territoryId, UUID userId) {
        this.id = new Key(territoryId, userId);
        this.createdAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "territory_id", nullable = false)
        private UUID territoryId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public Key(UUID territoryId, UUID userId) {
            this.territoryId = territoryId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key k)) {
                return false;
            }
            return Objects.equals(territoryId, k.territoryId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(territoryId, userId);
        }
    }
}
