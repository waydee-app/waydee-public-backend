package com.waydee.marketplace.infrastructure;

import com.waydee.marketplace.domain.ListingLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ListingLikeRepository extends JpaRepository<ListingLike, ListingLike.Key> {

    /** Liste ekranında "beğendim mi" bilgisi TEK sorguda çekilir (N+1 yok). */
    @Query("select l.id.listingId from ListingLike l where l.id.userId = :userId and l.id.listingId in :listingIds")
    List<UUID> likedListingIds(UUID userId, Collection<UUID> listingIds);
}
