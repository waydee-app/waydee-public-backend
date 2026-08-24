package com.waydee.social.infrastructure;

import com.waydee.social.domain.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    List<Collection> findByOwnerIdOrderByPositionAsc(UUID ownerId);

    /** Sayfalı liste — referans ekranında her sekmenin altında sayfalama var. */
    Page<Collection> findByOwnerIdOrderByPositionAsc(UUID ownerId, Pageable pageable);

    long countByOwnerId(UUID ownerId);

    /**
     * Sayaç <b>atomik</b> güncellenir (projenin diğer sayaçlarıyla aynı desen):
     * önce oku-sonra-yaz, iki gönderi aynı anda eklenince birini kaybederdi.
     *
     * <p>🔴 <b>{@code flushAutomatically} ŞART.</b> Yalnız {@code clearAutomatically}
     * yazılırsa, aynı transaction'da hemen önce yapılan {@code save()}'in
     * <b>bekleyen INSERT'i flush edilmeden</b> persistence context temizlenir ve
     * satır <b>sessizce kaybolur</b> — UPDATE yine koşar. Ölçüldü: koleksiyona iki
     * kez gönderi eklendi, {@code collection_posts} <b>0 satır</b>, {@code item_count}
     * <b>2</b>. Sayacın veriden kopması en sinsi hatadır: ekran dolu görünür, içerik yoktur.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Collection c SET c.itemCount = c.itemCount + :delta WHERE c.id = :id AND c.itemCount + :delta >= 0")
    void bumpItemCount(@Param("id") UUID id, @Param("delta") int delta);
}
