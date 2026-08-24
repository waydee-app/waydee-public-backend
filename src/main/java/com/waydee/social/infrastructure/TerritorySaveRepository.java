package com.waydee.social.infrastructure;

import com.waydee.social.domain.TerritorySave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TerritorySaveRepository extends JpaRepository<TerritorySave, TerritorySave.Key> {

    /** Liste ekranlarında "kaydettim mi" TEK sorguda çözülür (N+1 yok). */
    @Query("select l.id.territoryId from TerritorySave l where l.id.userId = :userId and l.id.territoryId in :ids")
    List<UUID> savedIds(UUID userId, Collection<UUID> ids);

    /** Trend skorunun kaydetme bileşeni: `[territoryId, adet]`. */
    @Query("""
            select l.id.territoryId, count(l)
            from TerritorySave l
            where l.createdAt >= :since
            group by l.id.territoryId
            """)
    List<Object[]> countsSince(Instant since);

    /** Kullanıcının kaydettiği daireler (profil sekmesi). */
    @Query("select l.id.territoryId from TerritorySave l where l.id.userId = :userId order by l.createdAt desc")
    List<UUID> savedByUser(UUID userId);
}
