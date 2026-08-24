package com.waydee.geo.infrastructure;

import com.waydee.geo.domain.Province;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvinceRepository extends JpaRepository<Province, UUID> {

    @EntityGraph(attributePaths = "country")
    List<Province> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "country")
    List<Province> findByCountryIdOrderByNameAsc(UUID countryId);

    boolean existsByCountryIdAndNameIgnoreCase(UUID countryId, String name);

    @Query(value = """
            SELECT * FROM provinces p
            WHERE p.active AND ST_Contains(p.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            ORDER BY ST_Distance(p.center, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Province> findActiveContaining(@Param("lng") double lng, @Param("lat") double lat);
}
