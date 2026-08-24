package com.waydee.messaging.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * İki kullanıcı arasındaki DM sohbeti. Katılımcı çifti sıralı saklanır
 * (userLo < userHi) — get-or-create yarışı UNIQUE kısıtla çözülür.
 * Okunmamış sayaçlar denormalizedir; atomik UPDATE ile güncellenir.
 */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_lo_id", nullable = false)
    private UUID userLoId;

    @Column(name = "user_hi_id", nullable = false)
    private UUID userHiId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 140)
    private String lastMessagePreview;

    @Column(name = "unread_lo", nullable = false)
    private int unreadLo;

    @Column(name = "unread_hi", nullable = false)
    private int unreadHi;

    /** Sohbeti başlatan (ilk mesajı atan) taraf — istek akışı için. */
    @Column(name = "requested_by_id")
    private UUID requestedById;

    /** false → karşı taraf için hâlâ "mesaj isteği"; gelen kutusuna düşmez. */
    @Column(name = "accepted", nullable = false)
    @lombok.Setter
    private boolean accepted = true;

    public Conversation(UUID a, UUID b) {
        /*
         * Çift her zaman sıralı: küçük id lo, büyük id hi.
         *
         * ⚠️ Sıralama `UuidOrder` ile yapılır, `UUID.compareTo` ile DEĞİL.
         * Java işaretli long, PostgreSQL işaretsiz bayt karşılaştırır; ikisi
         * farklı sıra verdiğinde `ck_conversations_order` patlıyor ve sohbet
         * açmak 500 dönüyordu (bkz. `UuidOrder`).
         */
        this.userLoId = UuidOrder.lo(a, b);
        this.userHiId = UuidOrder.hi(a, b);
    }

    public boolean isParticipant(UUID userId) {
        return userLoId.equals(userId) || userHiId.equals(userId);
    }

    public UUID otherOf(UUID userId) {
        return userLoId.equals(userId) ? userHiId : userLoId;
    }

    public int unreadFor(UUID userId) {
        return userLoId.equals(userId) ? unreadLo : unreadHi;
    }

    /** Sohbeti isteğe çevirir: başlatan taraf hariç kimse gelen kutusunda görmez. */
    public void markAsRequest(UUID requesterId) {
        this.requestedById = requesterId;
        this.accepted = false;
    }

    public void accept() {
        this.accepted = true;
    }

    /** Bu kullanıcı için sohbet "istek" mi? (isteği başlatan için değildir.) */
    public boolean isRequestFor(UUID userId) {
        return !accepted && requestedById != null && !requestedById.equals(userId);
    }
}
