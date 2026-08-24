package com.waydee.geo.infrastructure;

import com.waydee.geo.domain.District;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DistrictRepository extends JpaRepository<District, UUID> {

    @EntityGraph(attributePaths = {"province", "province.country"})
    List<District> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"province", "province.country"})
    List<District> findByProvinceIdOrderByNameAsc(UUID provinceId);

    boolean existsByProvinceIdAndNameIgnoreCase(UUID provinceId, String name);

    @Query(value = """
            SELECT * FROM districts d
            WHERE d.active AND ST_Contains(d.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            ORDER BY ST_Distance(d.center, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT 1
            """, nativeQuery = true)
    Optional<District> findActiveContaining(@Param("lng") double lng, @Param("lat") double lat);
}
