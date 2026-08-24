package com.waydee.messaging.infrastructure;

import com.waydee.messaging.domain.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByUserLoIdAndUserHiId(UUID userLoId, UUID userHiId);

    /**
     * Gelen kutusu: kabul edilmiş sohbetler + kendi başlattıklarım.
     * (Bana gelen ve henüz kabul etmediğim istekler burada GÖRÜNMEZ.)
     */
    @Query("""
            select c from Conversation c
            where (c.userLoId = :userId or c.userHiId = :userId)
              and (c.accepted = true or c.requestedById = :userId)
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    List<Conversation> findMine(@Param("userId") UUID userId, Pageable pageable);

    /** Mesaj istekleri: bana gelen, henüz kabul etmediğim sohbetler. */
    @Query("""
            select c from Conversation c
            where (c.userLoId = :userId or c.userHiId = :userId)
              and c.accepted = false and c.requestedById <> :userId
              and c.lastMessageAt is not null
            order by c.lastMessageAt desc
            """)
    List<Conversation> findRequests(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select count(c) from Conversation c
            where (c.userLoId = :userId or c.userHiId = :userId)
              and c.accepted = false and c.requestedById <> :userId
              and c.lastMessageAt is not null
            """)
    long countRequests(@Param("userId") UUID userId);

    /**
     * Mesaj gönderiminde sohbeti atomik günceller: son mesaj + karşı tarafın
     * okunmamış sayacı. Yarış yok (x = x + 1 deseni).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Conversation c
            set c.lastMessageAt = :at,
                c.lastMessagePreview = :preview,
                c.unreadLo = c.unreadLo + (case when c.userLoId = :recipientId then 1 else 0 end),
                c.unreadHi = c.unreadHi + (case when c.userHiId = :recipientId then 1 else 0 end)
            where c.id = :id
            """)
    void touchOnMessage(@Param("id") UUID id, @Param("at") Instant at,
                        @Param("preview") String preview, @Param("recipientId") UUID recipientId);

    /** Sohbeti okundu işaretle: yalnız kendi sayacın sıfırlanır. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Conversation c
            set c.unreadLo = (case when c.userLoId = :userId then 0 else c.unreadLo end),
                c.unreadHi = (case when c.userHiId = :userId then 0 else c.unreadHi end)
            where c.id = :id
            """)
    void resetUnread(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Nav rozeti: kabul edilmiş sohbetlerdeki toplam okunmamış mesaj (istekler ayrı sayılır). */
    @Query("""
            select coalesce(sum(case when c.userLoId = :userId then c.unreadLo else c.unreadHi end), 0)
            from Conversation c
            where (c.userLoId = :userId or c.userHiId = :userId)
              and (c.accepted = true or c.requestedById = :userId)
            """)
    long totalUnread(@Param("userId") UUID userId);
}
