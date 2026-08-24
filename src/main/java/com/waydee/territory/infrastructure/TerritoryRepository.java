package com.waydee.territory.infrastructure;

import com.waydee.territory.domain.Territory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TerritoryRepository extends JpaRepository<Territory, UUID> {

    @EntityGraph(attributePaths = "owner")
    List<Territory> findByOwnerIdOrderByPurchasedAtDesc(UUID ownerId);

    @Query("""
            SELECT t FROM Territory t JOIN FETCH t.owner
            WHERE t.status = com.waydee.territory.domain.TerritoryStatus.ACTIVE
            """)
    List<Territory> findAllActiveWithOwner();

    /**
     * Kirası dolmuş ama hâlâ ACTIVE görünen bölgeler (zamanlanmış süpürme işi).
     * `idx_territories_expiry` kısmi indeksi tam bu sorgu için vardır.
     */
    @Query("""
            SELECT t FROM Territory t
            WHERE t.status = :status AND t.expiresAt < :now
            """)
    List<Territory> findLapsed(java.time.Instant now, com.waydee.territory.domain.TerritoryStatus status);

    /** Süresi yaklaşan bölgeler — sahiplerine hatırlatma bildirimi için. */
    @Query("""
            SELECT t FROM Territory t JOIN FETCH t.owner
            WHERE t.status = com.waydee.territory.domain.TerritoryStatus.ACTIVE
              AND t.expiresAt BETWEEN :from AND :to
            """)
    List<Territory> findExpiringBetween(java.time.Instant from, java.time.Instant to);

    /** Beğeni/kaydetme sayaçları ATOMİK — eşzamanlı beğenide kayıp güncelleme olmaz. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Territory t set t.likeCount = t.likeCount + :delta where t.id = :id")
    void adjustLikeCount(UUID id, int delta);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Territory t set t.saveCount = t.saveCount + :delta where t.id = :id")
    void adjustSaveCount(UUID id, int delta);

    /** Ada göre arama — aktif, görünür ve gizli olmayan hesapların bölgeleri. */
    @Query("""
            SELECT t FROM Territory t JOIN FETCH t.owner o
            WHERE t.status = com.waydee.territory.domain.TerritoryStatus.ACTIVE
              AND t.hidden = false
              AND lower(t.name) LIKE lower(concat('%', :query, '%'))
            ORDER BY t.likeCount DESC, t.purchasedAt DESC
            """)
    List<Territory> search(String query, org.springframework.data.domain.Pageable pageable);

    /** Haritanın gördüğü küme: aktif VE admin tarafından gizlenmemiş. */
    @Query("""
            SELECT t FROM Territory t JOIN FETCH t.owner
            WHERE t.status = com.waydee.territory.domain.TerritoryStatus.ACTIVE
              AND t.hidden = false
            """)
    List<Territory> findAllVisibleWithOwner();

    /**
     * Admin listesi: gizli/pasif dahil her şey, isim veya sahip adına göre süzülebilir.
     * JOIN FETCH tekil (to-one) olduğu için sayfalama SQL'de yapılır (koleksiyon
     * fetch'i yok → HHH000104 riski yok). Sayım sorgusu elle verilir; türetilen
     * sayım JOIN FETCH ile patlar.
     *
     * ⚠️ {@code :query IS NULL} KULLANMA — PostgreSQL tipsiz null parametreyi
     * {@code bytea} çıkarımlar ve {@code lower(bytea) does not exist} hatası verir.
     * Süzgeç yokken boş string geçilir: {@code LIKE '%%'} her satırı tutar.
     */
    @Query(value = """
            SELECT t FROM Territory t JOIN FETCH t.owner o
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(o.username) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY t.purchasedAt DESC
            """,
            countQuery = """
            SELECT COUNT(t) FROM Territory t JOIN t.owner o
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(o.username) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    org.springframework.data.domain.Page<Territory> adminSearch(@Param("query") String query,
                                                                org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = "owner")
    Optional<Territory> findWithOwnerById(UUID id);

    /**
     * Yeni daire mevcut aktif alanlarla kesişiyor mu? (PostGIS spatial index kullanır)
     */
    @Query(value = """
            SELECT EXISTS(
                SELECT 1 FROM territories t
                WHERE t.status = 'ACTIVE'
                  AND ST_Intersects(t.boundary, ST_GeomFromText(:wkt, 4326))
            )
            """, nativeQuery = true)
    boolean existsActiveIntersecting(@Param("wkt") String wkt);

    /**
     * Satın alma yarış koşullarını global sıraya sokan advisory lock.
     * Transaction bitiminde otomatik bırakılır.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('waydee-territory-purchase'))", nativeQuery = true)
    void acquirePurchaseLock();

    long countByStatus(com.waydee.territory.domain.TerritoryStatus status);

    /**
     * Bir sahibin tum bolgelerini geri alir (hesap silme).
     *
     * <p>UYARI: satirlar SILINMEZ - faturalar ve satin almalar bunlara bagli.
     * REVOKED durumu bolgeyi haritadan dusurur ama gecmisi korur.
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Territory t SET t.status = com.waydee.territory.domain.TerritoryStatus.REVOKED "
            + "WHERE t.owner.id = :ownerId AND t.status <> com.waydee.territory.domain.TerritoryStatus.REVOKED")
    int revokeAllByOwner(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId);
}
