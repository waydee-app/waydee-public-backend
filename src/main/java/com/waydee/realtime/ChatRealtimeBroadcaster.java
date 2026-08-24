package com.waydee.realtime;

import com.waydee.messaging.application.event.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waydee.realtime.cluster.ClusterBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Mesaj commit'lendikten sonra iki tarafa da kullanıcı-kuyruğundan teslim eder.
 * Alıcı: canlı mesaj. Gönderen: diğer cihazları + optimistic eşleme yankısı.
 * Kuyruk kullanıcıya özeldir (StompPrincipal.getName() = userId) — sızıntı yok.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRealtimeBroadcaster {

    public static final String MESSAGES_QUEUE = "/queue/messages";

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        broadcaster.sendToUser(event.recipientId().toString(), MESSAGES_QUEUE, event.payload());
        broadcaster.sendToUser(event.senderId().toString(), MESSAGES_QUEUE, event.payload());
        log.debug("DM teslim edildi: {} -> {}", event.senderId(), event.recipientId());
    }
}
