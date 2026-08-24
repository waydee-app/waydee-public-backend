package com.waydee.realtime;

import com.waydee.territory.application.event.TerritoryPurchasedEvent;
import com.waydee.territory.application.event.TerritoryRemovedEvent;
import com.waydee.territory.application.event.TerritoryStyleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waydee.realtime.cluster.ClusterBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Domain event → STOMP köprüsü. İş mantığı bu sınıfı bilmez;
 * yalnızca DomainEventPublisher'a event basar.
 *
 * <p>🔴 Teslim artık {@link com.waydee.realtime.cluster.ClusterBroadcaster}
 * üzerinden, yani <b>Redis Pub/Sub ile tüm task'lara</b> yapılır. Eskiden
 * doğrudan süreç içi broker'a yazılıyordu ve ikinci bir task açılamıyordu
 * (ölçek analizi bulgusu K3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TerritoryRealtimeBroadcaster {

    public static final String TERRITORIES_TOPIC = "/topic/territories";

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerritoryPurchased(TerritoryPurchasedEvent event) {
        broadcaster.broadcast(TERRITORIES_TOPIC, Map.of(
                "type", "TERRITORY_PURCHASED",
                "territoryId", event.territoryId().toString(),
                "buyerUsername", event.buyerUsername(),
                "feature", event.feature()));
        log.debug("Satın alma yayını yapıldı: {}", event.territoryId());
    }

    /** Görünüm (renk/efekt/ad) değişince haritalar aynı feature'ı yerinde günceller. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerritoryStyleChanged(TerritoryStyleChangedEvent event) {
        broadcaster.broadcast(TERRITORIES_TOPIC, Map.of(
                "type", "TERRITORY_UPDATED",
                "territoryId", event.territoryId().toString(),
                "feature", event.feature()));
        log.debug("Bölge görünümü yayınlandı: {}", event.territoryId());
    }

    /** Bölge pasife alındı/gizlendi → istemciler feature'ı haritadan siler. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerritoryRemoved(TerritoryRemovedEvent event) {
        broadcaster.broadcast(TERRITORIES_TOPIC, Map.of(
                "type", "TERRITORY_REMOVED",
                "territoryId", event.territoryId().toString()));
        log.debug("Bölge kaldırma yayını yapıldı: {}", event.territoryId());
    }
}
