package com.waydee.social.infrastructure;

import com.waydee.social.domain.ProfileLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileLinkRepository extends JpaRepository<ProfileLink, UUID> {

    List<ProfileLink> findByOwnerIdOrderByPositionAsc(UUID ownerId);

    /** Sayfalı liste — referansta bağlantı ızgarasının altında sayfalama var. */
    Page<ProfileLink> findByOwnerIdOrderByPositionAsc(UUID ownerId, Pageable pageable);

    /** Vitrin: yalnız <b>aktif</b> bağlantılar — pasif olan profilde görünmez. */
    Page<ProfileLink> findByOwnerIdAndActiveTrueOrderByPositionAsc(UUID ownerId, Pageable pageable);

    long countByOwnerId(UUID ownerId);

    /**
     * <b>Toplam tıklama sayacını bir artırır</b> (V46).
     *
     * <p>🔴 Bu sorgu 16 Ağu 2026'ya kadar <b>yoktu</b>: kolon vardı, arayüzde
     * gösteriliyordu, ama artıran hiçbir şey yoktu — kullanıcı her zaman sıfır
     * olan bir sayıya bakıyordu.
     *
     * <p>⚠️ Atomik {@code UPDATE}: "oku, artır, kaydet" üç adımı olsaydı
     * eşzamanlı iki tıklama birbirini ezerdi. (Projedeki beğeni/yorum
     * sayaçlarıyla aynı desen.)
     * ⚠️ {@code clearAutomatically} <b>kullanılmaz</b> — entity'yi detach edip
     * sonraki yazımları sessizce yutuyor (vault, 86. tur).
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE ProfileLink l SET l.clickCount = l.clickCount + 1 WHERE l.id = :id")
    void bumpClickCount(@org.springframework.data.repository.query.Param("id") UUID id);
}
