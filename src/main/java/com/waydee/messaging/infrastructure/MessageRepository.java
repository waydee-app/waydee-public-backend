package com.waydee.messaging.infrastructure;

import com.waydee.messaging.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Keyset (seek) sayfalama — OFFSET yok: büyük sohbetlerde de sabit maliyet.
     * idx_messages_conv_created indeksini kullanır.
     */
    @Query("""
            select m from Message m
            where m.conversationId = :conversationId and m.createdAt < :before
            order by m.createdAt desc, m.id desc
            """)
    List<Message> pageBefore(@Param("conversationId") UUID conversationId,
                             @Param("before") Instant before, Pageable pageable);

    /** İdempotent gönderim: aynı clientMessageId ile tekrar POST aynı mesajı döndürür. */
    Optional<Message> findFirstByConversationIdAndSenderIdAndClientMessageId(
            UUID conversationId, UUID senderId, String clientMessageId);

    /** Mesaj isteği reddedilince sohbetin tüm mesajları silinir. */
    void deleteByConversationId(UUID conversationId);
}
