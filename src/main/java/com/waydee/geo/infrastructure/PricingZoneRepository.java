package com.waydee.geo.infrastructure;

import com.waydee.geo.domain.PricingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingZoneRepository extends JpaRepository<PricingZone, UUID> {

    List<PricingZone> findAllByOrderByPriorityDescCreatedAtDesc();

    /**
     * Noktayı kapsayan aktif bölge (GIST indeksini kullanır).
     * Bölgeler üst üste çizilebildiği için kazanan **en pahalı** olandır;
     * eşitlikte öncelik, sonra en yeni kayıt belirler.
     */
    @Query(value = """
            SELECT * FROM pricing_zones z
            WHERE z.active AND ST_Contains(z.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            ORDER BY z.price_per_km2 DESC, z.priority DESC, z.created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<PricingZone> findActiveContaining(@Param("lng") double lng, @Param("lat") double lat);

    /**
     * Verilen daire herhangi bir aktif bölgenin **kenar çizgisini** kesiyor mu?
     * Kesiyorsa alan hem içeride hem dışarıda kalır — buna izin verilmez.
     */
    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM pricing_zones z
                WHERE z.active
                  AND ST_Intersects(ST_Boundary(z.boundary), ST_GeomFromText(:wkt, 4326))
            )
            """, nativeQuery = true)
    boolean existsCrossingBoundary(@Param("wkt") String wkt);
}
