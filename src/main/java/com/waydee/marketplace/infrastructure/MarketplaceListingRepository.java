package com.waydee.marketplace.infrastructure;

import com.waydee.marketplace.domain.ListingStatus;
import com.waydee.marketplace.domain.MarketplaceListing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceListingRepository extends JpaRepository<MarketplaceListing, UUID> {

    @EntityGraph(attributePaths = "owner")
    Optional<MarketplaceListing> findWithOwnerById(UUID id);

    /** Bir pazardaki görünür stantlar (harita + vitrin). */
    @Query("""
            select l from MarketplaceListing l join fetch l.owner
            where l.marketplaceId = :marketplaceId
              and l.status = com.waydee.marketplace.domain.ListingStatus.APPROVED
            order by l.featured desc, l.likeCount desc, l.submittedAt asc
            """)
    List<MarketplaceListing> findApproved(UUID marketplaceId);

    /** Tüm pazarların görünür stantları — harita katmanı tek sorguda dolar. */
    @Query("""
            select l from MarketplaceListing l join fetch l.owner
            where l.marketplaceId in :marketplaceIds
              and l.status = com.waydee.marketplace.domain.ListingStatus.APPROVED
            """)
    List<MarketplaceListing> findApprovedIn(Collection<UUID> marketplaceIds);

    /**
     * Aynı kullanıcının o pazardaki AKTİF başvurusu (taslak/bekleyen/onaylı).
     * Reddedilen ve geri çekilen sayılmaz — tekrar başvurabilsin.
     */
    @Query("""
            select l from MarketplaceListing l
            where l.marketplaceId = :marketplaceId and l.owner.id = :ownerId
              and l.status in (com.waydee.marketplace.domain.ListingStatus.DRAFT,
                               com.waydee.marketplace.domain.ListingStatus.PENDING,
                               com.waydee.marketplace.domain.ListingStatus.APPROVED)
            """)
    Optional<MarketplaceListing> findActiveByOwner(UUID marketplaceId, UUID ownerId);

    @EntityGraph(attributePaths = "owner")
    List<MarketplaceListing> findByOwnerIdOrderBySubmittedAtDesc(UUID ownerId);

    /**
     * Yönetim kuyruğu. Sayfalı + JOIN FETCH birlikte kullanıldığı için sayım
     * sorgusu ELLE verilir (türetilen sayım "owner of the fetched association
     * was not present in the select list" hatası verirdi — vault tuzağı).
     */
    @Query(value = """
            select l from MarketplaceListing l join fetch l.owner
            where (:marketplaceId is null or l.marketplaceId = :marketplaceId)
              and l.status = :status
            order by l.submittedAt asc
            """,
            countQuery = """
            select count(l) from MarketplaceListing l
            where (:marketplaceId is null or l.marketplaceId = :marketplaceId)
              and l.status = :status
            """)
    Page<MarketplaceListing> findForReview(UUID marketplaceId, ListingStatus status, Pageable pageable);

    long countByStatus(ListingStatus status);

    long countByMarketplaceIdAndStatus(UUID marketplaceId, ListingStatus status);

    /** Yeni stantın indeksi — mevcut en büyük indeksin bir fazlası. */
    @Query("select coalesce(max(l.spotIndex), -1) from MarketplaceListing l where l.marketplaceId = :marketplaceId")
    int maxSpotIndex(UUID marketplaceId);

    /** Beğeni sayacı atomik — eşzamanlı beğenide kayıp güncelleme olmaz. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MarketplaceListing l set l.likeCount = l.likeCount + :delta where l.id = :id")
    void adjustLikeCount(UUID id, int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MarketplaceListing l set l.viewCount = l.viewCount + 1 where l.id = :id")
    void incrementViewCount(UUID id);
}
