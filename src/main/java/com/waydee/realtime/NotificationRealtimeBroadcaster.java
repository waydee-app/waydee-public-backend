package com.waydee.realtime;

import com.waydee.realtime.cluster.ClusterBroadcaster;
import com.waydee.social.application.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * <b>Bildirimi anında sahibine iletir</b> (18 Ağu 2026).
 *
 * <p>🔴 Bildirimler o güne kadar yalnız <b>yoklamayla</b> geliyordu: zil 30
 * saniyede bir sayacı soruyor, liste ancak sayfa yenilenince tazeleniyordu.
 * Kullanıcının şikâyeti buydu. Canlı taşıma projede <b>zaten vardı</b>
 * (mesajlar, bölge, aktivite) — yalnız bildirimler ona bağlanmamıştı.
 *
 * <p>⚠️ Kuyruk <b>kullanıcıya özeldir</b> ({@code StompPrincipal.getName()} =
 * userId), yani başkasının bildirimi kimseye sızmaz —
 * {@link ChatRealtimeBroadcaster} ile aynı kural.
 *
 * <p>⚠️ {@code AFTER_COMMIT}: istemci, veritabanında henüz görünmeyen bir
 * bildirim için listeyi tazelerse <b>eski</b> listeyi alır ve rozet yalan
 * söyler. Bildirim yazılamazsa da hiçbir şey yayınlanmamalıdır.
 *
 * <p>⚠️ Yük yalnız <b>haber</b>dir, bildirimin kendisi değil. Gövdeyi sokete
 * koymak; aktör adı, avatarı ve metni <b>iki ayrı yerde</b> üretmek ve ikisinin
 * sessizce ayrışması demekti. İstemci haberi alıp kendi ucundan okur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimeBroadcaster {

    public static final String NOTIFICATIONS_QUEUE = "/queue/notifications";

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        broadcaster.sendToUser(
                event.recipientId().toString(),
                NOTIFICATIONS_QUEUE,
                Map.of("type", event.type()));
        log.debug("Bildirim teslim edildi: {} -> {}", event.type(), event.recipientId());
    }
}
