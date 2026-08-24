package com.waydee.social.infrastructure;

import com.waydee.social.domain.TerritoryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TerritoryProfileRepository extends JpaRepository<TerritoryProfile, UUID> {

    @Modifying
    @Query("UPDATE TerritoryProfile p SET p.postCount = p.postCount + :delta WHERE p.territoryId = :territoryId")
    void adjustPostCount(@Param("territoryId") UUID territoryId, @Param("delta") int delta);
}
