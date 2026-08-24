package com.waydee.realtime;

import com.waydee.geo.application.event.PricingZoneChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waydee.realtime.cluster.ClusterBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Fiyat bölgesi değişikliklerini kullanıcı haritalarına anında yayınlar.
 * İstemci bu sinyalle /map/regions'ı tazeler — kazanan bölge sıralaması
 * (fiyat DESC → öncelik DESC) her zaman sunucuda çözülür.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegionRealtimeBroadcaster {

    public static final String REGIONS_TOPIC = "/topic/regions";

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPricingZoneChanged(PricingZoneChangedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "PRICING_ZONE_" + event.action());
        payload.put("zoneId", event.zoneId().toString());
        if (event.feature() != null) {
            payload.put("feature", event.feature());
        }
        broadcaster.broadcast(REGIONS_TOPIC, payload);
        log.debug("Fiyat bölgesi yayını: {} {}", event.action(), event.zoneId());
    }
}
