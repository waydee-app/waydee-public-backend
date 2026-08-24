package com.waydee.social.infrastructure;

import com.waydee.social.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /** Trend skoru — dönemde açılan gönderiler: `[territoryId, adet]`. */
    @org.springframework.data.jpa.repository.Query("""
            select p.territoryId, count(p)
            from Post p
            where p.createdAt >= :since and p.deletedAt is null
            group by p.territoryId
            """)
    java.util.List<Object[]> countsByTerritorySince(java.time.Instant since);


    @EntityGraph(attributePaths = {"author", "media", "media.media"})
    Optional<Post> findWithDetailsById(UUID id);

    /**
     * İki adımlı sayfalamanın 1. adımı: yalnız id'ler (gerçek SQL LIMIT).
     * Collection fetch + Pageable birlikte kullanılamaz (bellekte sayfalar — HHH000104).
     */
    @Query("select p.id from Post p where p.territoryId = :territoryId and p.deletedAt is null order by p.createdAt desc")
    Page<UUID> pageIdsByTerritory(@Param("territoryId") UUID territoryId, Pageable pageable);

    /** Bir kullanıcının tüm gönderileri (Instagram tarzı profil ızgarası). */
    @Query("select p.id from Post p where p.author.id = :authorId and p.deletedAt is null order by p.createdAt desc")
    Page<UUID> pageIdsByAuthor(@Param("authorId") UUID authorId, Pageable pageable);

    /** Keşfet: gizli olmayan + aktif hesapların gönderileri (görüntüleyenin kendisi hariç). */
    @Query("""
            select p.id from Post p
            where p.deletedAt is null
              and p.author.privateAccount = false
              and p.author.status = com.waydee.identity.domain.UserStatus.ACTIVE
              and p.author.id <> :viewerId
            order by p.createdAt desc
            """)
    Page<UUID> pageExploreIds(@Param("viewerId") UUID viewerId, Pageable pageable);

    /**
     * Ana akış: takip ettiklerimin + kendi gönderilerim (en yeniden eskiye).
     * Takip ilişkisi ACCEPTED olmalı.
     */
    @Query("""
            select p.id from Post p
            where p.deletedAt is null
              and (p.author.id = :viewerId
                   or exists (select f from Follow f
                              where f.followerId = :viewerId and f.followeeId = p.author.id
                                and f.status = com.waydee.identity.domain.FollowStatus.ACCEPTED))
            order by p.createdAt desc
            """)
    Page<UUID> pageFollowingIds(@Param("viewerId") UUID viewerId, Pageable pageable);

    /** 2. adım: sayfadaki id'ler için detaylı fetch (author + media). */
    @EntityGraph(attributePaths = {"author", "media", "media.media"})
    @Query("select p from Post p where p.id in :ids")
    List<Post> findAllWithDetailsByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Bir kullanicinin TUM gonderilerini yumusak siler (hesap silme).
     *
     * <p>UYARI: satirlar SILINMEZ - yorumlar, begeniler ve etiketler onlara
     * bagli; kaskad silme o tablolari da goturuurdu. Yumusak silme icerigi her
     * yerden gizlerken referans butunlugunu korur.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.deletedAt = :now WHERE p.author.id = :authorId AND p.deletedAt IS NULL")
    int softDeleteByAuthor(@Param("authorId") UUID authorId, @Param("now") java.time.Instant now);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :postId")
    void adjustLikeCount(@Param("postId") UUID postId, @Param("delta") int delta);

    /**
     * Kaydetme sayaci.
     *
     * <p>⚠️ Sayac <b>eksiye inemez</b>: ayni kaydi iki istek ayni anda
     * kaldirmaya calisirsa (ikisi de kaydi "duruyor" gorebilir) sayac -1'e
     * duserdi. Kontrol tek ifadenin icinde — okuyup yazmak yaris acardi.
     */
    @Modifying
    @Query("UPDATE Post p SET p.saveCount = CASE WHEN p.saveCount + :delta < 0 THEN 0 ELSE p.saveCount + :delta END WHERE p.id = :postId")
    void adjustSaveCount(@Param("postId") UUID postId, @Param("delta") int delta);

    /**
     * ⚠️ Sayaç <b>eksiye inemez</b>. Yorum silme eklendikten sonra negatif delta
     * mümkün oldu; aynı yorumu iki istek aynı anda silmeye çalışırsa (ikisi de
     * kaydı "silinmemiş" görebilir) sayaç -1'e düşer ve arayüzde saçma bir
     * değer görünürdü. Kontrol tek ifadenin içinde — okuyup yazmak yarış açardı.
     */
    @Modifying
    @Query("""
            UPDATE Post p
               SET p.commentCount = CASE WHEN p.commentCount + :delta < 0 THEN 0
                                         ELSE p.commentCount + :delta END
             WHERE p.id = :postId
            """)
    void adjustCommentCount(@Param("postId") UUID postId, @Param("delta") int delta);

    // flush: bekleyen rsvp insert/delete önce yazılır; clear: taze fetch bayat L1 önbelleğine takılmaz.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.goingCount = p.goingCount + :delta WHERE p.id = :postId")
    void adjustGoingCount(@Param("postId") UUID postId, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.interestedCount = p.interestedCount + :delta WHERE p.id = :postId")
    void adjustInterestedCount(@Param("postId") UUID postId, @Param("delta") int delta);

    long countByDeletedAtIsNull();

    /**
     * Raporlama: bir kullanıcının gönderi sayısı ve topladığı beğeni/yorum —
     * tek sorguda `[gönderi, beğeni, yorum]` (sayaçlar zaten denormalize).
     */
    @Query("""
            select count(p), coalesce(sum(p.likeCount), 0), coalesce(sum(p.commentCount), 0)
            from Post p
            where p.author.id = :authorId and p.deletedAt is null
            """)
    Object[] engagementByAuthor(@Param("authorId") UUID authorId);

    /** Dönem içinde paylaşılan gönderi sayısı. */
    long countByAuthorIdAndDeletedAtIsNullAndCreatedAtAfter(UUID authorId, Instant since);

    /** Sahibin gönderilerinin toplam kaydedilme sayısı (Analytics "Total Saves"). */
    @Query("SELECT COALESCE(SUM(p.saveCount), 0) FROM Post p WHERE p.author.id = :authorId AND p.deletedAt IS NULL")
    long sumSaveCountByAuthor(@Param("authorId") UUID authorId);

    /**
     * Profil ızgarası — kullanıcının silinmemiş gönderileri, en yeniden eskiye.
     *
     * <p>⚠️ `media` KOLEKSİYONU fetch edilir ama SAYFALAMA YOKTUR: koleksiyon
     * fetch + Pageable birlikte kullanılırsa Hibernate bellekte sayfalar
     * (HHH000104) ve tüm satırları çeker (vault'ta kayıtlı tuzak).
     */
    @EntityGraph(attributePaths = "media")
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<Post> findProfilePosts(@Param("authorId") UUID authorId);

    /**
     * Aynı sorgunun <b>sayfalı</b> hâli — referans profilinde her sekmenin
     * altında sayfalama vardır ve tüm gönderileri tek seferde çekmek
     * ızgarayı büyüdükçe yavaşlatırdı.
     *
     * <p>⚠️ Sıra {@code createdAt DESC} ile <b>kararlıdır</b>: sayfa 2'ye
     * geçerken sıralama değişirse kullanıcı aynı gönderiyi iki kez görür.
     */
    @EntityGraph(attributePaths = "media")
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId AND p.deletedAt IS NULL AND p.archived = false ORDER BY p.createdAt DESC, p.id DESC")
    Page<Post> findProfilePosts(@Param("authorId") UUID authorId, Pageable pageable);

    /** Vitrin profilindeki sekme rozetinin sayısı — satır çekmeden. */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId AND p.deletedAt IS NULL AND p.archived = false")
    long countProfilePosts(@Param("authorId") UUID authorId);

    /**
     * Bir koleksiyondaki gönderiler — <b>sayfalı</b>, kullanıcının ekleme sırasıyla.
     *
     * <p>⚠️ Sorgu bilerek {@code CollectionPostRepository}'de değil BURADA:
     * {@code @EntityGraph} repository'nin <b>kök tipine</b> uygulanır. Orada
     * yazıldığında Hibernate {@code media} alanını {@code CollectionPost}
     * üstünde arayıp patlıyordu (ölçüldü: 500).
     */
    @EntityGraph(attributePaths = "media")
    @Query("""
            SELECT p FROM Post p, CollectionPost cp
            WHERE cp.id.postId = p.id AND cp.id.collectionId = :collectionId
              AND p.deletedAt IS NULL
            ORDER BY cp.position ASC
            """)
    Page<Post> findCollectionPosts(@Param("collectionId") UUID collectionId, Pageable pageable);

    /**
     * <b>Herkese açık tek gönderi</b> — kimliksiz görüntüleme ve SEO için.
     *
     * <p>Kapı gönderinin kendisinde DEĞİL, <b>yazarında</b>: gizli ya da askıya
     * alınmış bir hesabın gönderisi kimliksiz hiç dönmez. Arşivlenmiş ve
     * silinmiş gönderi de yok sayılır.
     *
     * <p>⚠️ {@code author} ve {@code media} fetch edilir: yanıt ikisine de
     * dokunuyor ve transaction dışında lazy koleksiyon patlardı (bu tuzak
     * {@code findWithDetailsById}'de bir kez yaşandı).
     */
    @EntityGraph(attributePaths = {"author", "media", "media.media"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.id = :id AND p.deletedAt IS NULL AND p.archived = false
              AND p.author.privateAccount = false
              AND p.author.status = com.waydee.identity.domain.UserStatus.ACTIVE
            """)
    Optional<Post> findPublicPost(@Param("id") UUID id);

    /**
     * <b>Paylaşılabilir gönderi</b> — gizlilik kararı ÇAĞIRANA bırakılır.
     *
     * <p>🔴 10 Ağu 2026: {@link #findPublicPost} yazarın <b>gizli olmamasını</b>
     * şart koşuyor. Bu, gizli bir hesabı <b>takip eden</b> kullanıcının o
     * hesabın gönderisine tıklayınca <b>"Gönderi bulunamadı"</b> almasına yol
     * açıyordu (kullanıcı bildirdi): profil sayfası artık takipçiye açık, ama
     * tek gönderi ucu hâlâ kapalıydı — aynı kuralın iki farklı yerde iki farklı
     * cevabı vardı.
     *
     * <p>Burada yalnız <b>gönderiye ait</b> koşullar var (silinmemiş,
     * arşivlenmemiş, yazar aktif); "bu kişi görebilir mi" sorusunu
     * {@code FollowService.canViewContent} yanıtlar.
     */
    @EntityGraph(attributePaths = {"author", "media", "media.media"})
    @Query("""
            SELECT p FROM Post p
            WHERE p.id = :id AND p.deletedAt IS NULL AND p.archived = false
              AND p.author.status = com.waydee.identity.domain.UserStatus.ACTIVE
            """)
    Optional<Post> findShareablePost(@Param("id") UUID id);

    /**
     * Site haritasına girecek gönderiler — açık hesapların yayındaki gönderileri.
     *
     * <p>⚠️ {@code updatedAt DESC}: sitemap'te {@code lastmod} bu değerden
     * üretilir, sıralama da onunla aynı olmalı ki sayfalama kararlı kalsın.
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.deletedAt IS NULL AND p.archived = false
              AND p.author.privateAccount = false
              AND p.author.status = com.waydee.identity.domain.UserStatus.ACTIVE
            ORDER BY p.updatedAt DESC
            """)
    Page<Post> findIndexablePosts(Pageable pageable);
}
