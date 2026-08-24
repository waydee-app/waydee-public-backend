package com.waydee.realtime;

import com.waydee.marketplace.application.event.MarketplaceChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waydee.realtime.cluster.ClusterBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Pazar yeri değişimlerini açık haritalara taşır.
 *
 * AFTER_COMMIT: onay geri alınırsa istemciye "onaylandı" sinyali gitmez.
 * İstemci sinyali görünce pazar/stant kaynaklarını tazeler (yükü burada
 * taşımayız — kimin neyi görebileceği okuma ucunda kararlaştırılır).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceRealtimeBroadcaster {

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(MarketplaceChangedEvent event) {
        broadcaster.broadcast("/topic/marketplaces", Map.of(
                "type", event.action(),
                "marketplaceId", event.marketplaceId().toString()));
        log.debug("Pazar yeri yayını: {} → {}", event.action(), event.marketplaceId());
    }
}
