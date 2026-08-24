package com.waydee.social.infrastructure;

import com.waydee.social.domain.TerritoryLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TerritoryLikeRepository extends JpaRepository<TerritoryLike, TerritoryLike.Key> {

    /** Liste ekranlarında "beğendim mi" TEK sorguda çözülür (N+1 yok). */
    @Query("select l.id.territoryId from TerritoryLike l where l.id.userId = :userId and l.id.territoryId in :ids")
    List<UUID> likedIds(UUID userId, Collection<UUID> ids);

    /** Trend skorunun beğeni bileşeni: `[territoryId, adet]`. */
    @Query("""
            select l.id.territoryId, count(l)
            from TerritoryLike l
            where l.createdAt >= :since
            group by l.id.territoryId
            """)
    List<Object[]> countsSince(Instant since);

    /** Kullanıcının beğendiği daireler (profil sekmesi). */
    @Query("select l.id.territoryId from TerritoryLike l where l.id.userId = :userId order by l.createdAt desc")
    List<UUID> likedByUser(UUID userId);
}
