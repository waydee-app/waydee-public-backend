package com.waydee.marketplace.infrastructure;

import com.waydee.marketplace.domain.StoreProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StoreProductRepository extends JpaRepository<StoreProduct, UUID> {

    /** Stüdyo listesi — gizlenmişler dahil, sahibi hepsini yönetir. */
    List<StoreProduct> findByListingIdOrderByPositionAsc(UUID listingId);

    /**
     * Cadde çizimi — birden çok mağazanın <b>görünür</b> ürünleri tek sorguda.
     * Gerekçe {@link StoreSettingsRepository#findByListingIdIn} ile aynı (N+1).
     */
    @Query("""
            SELECT p FROM StoreProduct p
             WHERE p.listingId IN :listingIds AND p.visible = true
             ORDER BY p.position ASC
            """)
    List<StoreProduct> findVisibleForListings(@Param("listingIds") Collection<UUID> listingIds);

    boolean existsByListingIdAndPostId(UUID listingId, UUID postId);

    int countByListingId(UUID listingId);

    /** Sıradaki konum — yeni ürün rafın SONUNA eklenir, araya girmez. */
    @Query("SELECT COALESCE(MAX(p.position), -1) + 1 FROM StoreProduct p WHERE p.listingId = :listingId")
    int nextPosition(@Param("listingId") UUID listingId);
}
