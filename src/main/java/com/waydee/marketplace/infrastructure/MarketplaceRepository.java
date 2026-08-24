package com.waydee.marketplace.infrastructure;

import com.waydee.marketplace.domain.Marketplace;
import com.waydee.marketplace.domain.MarketplaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceRepository extends JpaRepository<Marketplace, UUID> {

    Optional<Marketplace> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Marketplace> findByStatusInOrderByCreatedAtDesc(Collection<MarketplaceStatus> statuses);

    /**
     * Sayaç ATOMİK güncellenir — iki başvuru aynı anda onaylanırsa
     * bellekte artırma kayıp güncelleme üretirdi (kontenjan aşılırdı).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Marketplace m set m.listingCount = m.listingCount + :delta where m.id = :id")
    void adjustListingCount(UUID id, int delta);

    /** Vitrin/harita: taslak ve arşiv hariç her şey. */
    @Query("""
            select m from Marketplace m
            where m.status in (com.waydee.marketplace.domain.MarketplaceStatus.OPEN,
                               com.waydee.marketplace.domain.MarketplaceStatus.CLOSED)
            order by m.createdAt desc
            """)
    List<Marketplace> findPublic();
}
