package com.waydee.social.infrastructure;

import com.waydee.social.domain.TerritoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TerritoryViewRepository extends JpaRepository<TerritoryView, UUID> {

    /**
     * Bu ziyaretçi bu bölgeyi <b>bugün</b> zaten gördü mü (V54).
     *
     * <p>⚠️ Bu yalnız bir <b>ön elemedir</b>, kural değil: kuralı tekil indeks
     * uyguluyor. Ön eleme, her görüntülemede bir INSERT denemesi (ve dolayısıyla
     * bir kısıt ihlali + rollback) üretmemek için var — yarışta ikinci satırı
     * durduran şey indekstir.
     */
    boolean existsByTerritoryIdAndViewerIdAndViewDay(UUID territoryId, UUID viewerId,
                                                     java.time.LocalDate viewDay);

    long countByTerritoryId(UUID territoryId);

    /** Kartın "Bugün Görüntülenme" sayacı ve "Trend" kararı bunu kullanır. */
    long countByTerritoryIdAndViewedAtAfter(UUID territoryId, Instant since);

    /** Trend skoru — TÜM bölgeler için tek GROUP BY: `[territoryId, adet]`. */
    @Query("""
            select v.territoryId, count(v)
            from TerritoryView v
            where v.viewedAt >= :since
            group by v.territoryId
            """)
    List<Object[]> countsByTerritorySince(Instant since);

    long countByTerritoryIdIn(Collection<UUID> territoryIds);

    @Query("select count(distinct v.viewerId) from TerritoryView v where v.territoryId = :tid")
    long countDistinctViewers(@Param("tid") UUID territoryId);

    @Query("select count(distinct v.viewerId) from TerritoryView v where v.territoryId in :tids")
    long countDistinctViewersIn(@Param("tids") Collection<UUID> territoryIds);

    List<TerritoryView> findTop50ByTerritoryIdOrderByViewedAtDesc(UUID territoryId);

    List<TerritoryView> findTop50ByTerritoryIdInOrderByViewedAtDesc(Collection<UUID> territoryIds);

    List<TerritoryView> findByTerritoryIdAndViewedAtAfterOrderByViewedAtAsc(UUID territoryId, Instant since);

    List<TerritoryView> findByTerritoryIdInAndViewedAtAfterOrderByViewedAtAsc(Collection<UUID> territoryIds, Instant since);

    // ------------------------------------------------------------ raporlama

    /** Bir dönemdeki görüntülenme sayısı (önceki dönemle karşılaştırma için). */
    long countByTerritoryIdInAndViewedAtBetween(Collection<UUID> territoryIds, Instant from, Instant to);

    /** Bölge bazında görüntülenme kırılımı: `[territoryId, toplam, tekil]`. */
    @Query("""
            select v.territoryId, count(v), count(distinct v.viewerId)
            from TerritoryView v
            where v.territoryId in :tids and v.viewedAt >= :since
            group by v.territoryId
            """)
    List<Object[]> statsByTerritory(@Param("tids") Collection<UUID> territoryIds, @Param("since") Instant since);

    /** Haftanın günü / saat dağılımı için ham zaman damgaları (dönem sınırlı). */
    @Query("select v.viewedAt from TerritoryView v where v.territoryId in :tids and v.viewedAt >= :since")
    List<Instant> viewTimes(@Param("tids") Collection<UUID> territoryIds, @Param("since") Instant since);

    /** En çok görüntüleyen kişiler: `[viewerId, adet, sonGörüntüleme]`. */
    @Query("""
            select v.viewerId, count(v), max(v.viewedAt)
            from TerritoryView v
            where v.territoryId in :tids and v.viewedAt >= :since
            group by v.viewerId
            order by count(v) desc
            """)
    List<Object[]> topViewers(@Param("tids") Collection<UUID> territoryIds, @Param("since") Instant since,
                              org.springframework.data.domain.Pageable pageable);
}
