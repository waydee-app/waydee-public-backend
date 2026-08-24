package com.waydee.geo.infrastructure;

import com.waydee.geo.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    Optional<Country> findByCode(String code);

    boolean existsByCode(String code);

    List<Country> findAllByOrderByNameAsc();

    @Query(value = """
            SELECT * FROM countries c
            WHERE c.active AND ST_Contains(c.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            ORDER BY ST_Distance(c.center, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Country> findActiveContaining(@Param("lng") double lng, @Param("lat") double lat);
}
