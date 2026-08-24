package com.waydee.realtime;

import com.waydee.social.application.event.ActivityRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waydee.realtime.cluster.ClusterBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Aktivite satırlarını (son hareketler) tüm istemcilere canlı yayınlar. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityRealtimeBroadcaster {

    public static final String ACTIVITY_TOPIC = "/topic/activity";

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivityRecorded(ActivityRecordedEvent event) {
        broadcaster.broadcast(ACTIVITY_TOPIC, event.payload());
        log.debug("Aktivite yayını: {}", event.payload().get("type"));
    }
}
