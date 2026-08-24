package com.waydee.social.infrastructure;

import com.waydee.social.domain.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

    boolean existsByStoryIdAndViewerId(UUID storyId, UUID viewerId);

    long countByStoryId(UUID storyId);

    @Query("select v.storyId from StoryView v where v.viewerId = :viewerId and v.storyId in :storyIds")
    List<UUID> findViewedStoryIds(@Param("viewerId") UUID viewerId, @Param("storyIds") List<UUID> storyIds);

    /**
     * Bir hikayeyi kimlerin gördüğü — <b>en son bakan üstte</b>.
     *
     * <p>🔴 16 Ağu 2026 — göz simgesinin arkasındaki liste. Sayı zaten
     * {@code countByStoryId} ile vardı ama <b>kim</b> sorusunun karşılığı yoktu.
     *
     * <p>⚠️ Kullanıcıyla <b>birleştirme</b> şart ve tek sorguda yapılır:
     * {@code StoryView} bakanı ilişki olarak değil <b>ham kimlikle</b> tutuyor,
     * yani satır satır kullanıcı çekmek klasik N+1 olurdu.
     *
     * <p>⚠️ Ad ve zaman <b>TEK sorgudan</b> gelir. İki ayrı sorgu (biri
     * kullanıcılar, biri zamanlar) yazıp indislerine göre eşlemek cazipti ama
     * aynı milisaniyede iki bakış olduğunda sıralama iki sorguda farklı
     * çözülebilir ve <b>yanlış kişiye yanlış saat</b> yazılırdı.
     *
     * <p>⚠️ Yapıcı ifadesi ({@code select new ...}) yerine <b>arayüz
     * izdüşümü</b> kullanıldı: hedef kayıt iç içe bir tipte olsaydı JPQL'de
     * ikili ad ({@code Dtos$Row}) yazmak gerekirdi ve bu Hibernate sürümüne
     * göre değişen kırılgan bir ayrıntıdır. İzdüşüm takma adlara bakar.
     */
    @Query("""
            select u.id as id, u.username as username, u.displayName as displayName,
                   u.avatarMediaId as avatarMediaId, v.viewedAt as viewedAt
            from StoryView v
              join User u on u.id = v.viewerId
            where v.storyId = :storyId
            order by v.viewedAt desc
            """)
    List<ViewerRow> findViewers(@Param("storyId") UUID storyId,
                                org.springframework.data.domain.Pageable pageable);

    /** {@link #findViewers} izdüşümü — takma adlar sorgudakiyle birebir. */
    interface ViewerRow {
        UUID getId();

        String getUsername();

        String getDisplayName();

        UUID getAvatarMediaId();

        java.time.Instant getViewedAt();
    }
}
