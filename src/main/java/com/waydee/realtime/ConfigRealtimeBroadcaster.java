package com.waydee.realtime;

import com.waydee.common.config.ClientConfigService.ClientConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.waydee.realtime.cluster.ClusterBroadcaster;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Çalışma zamanı ayarı değişti → açık istemcilere haber ver.
 *
 * Mapbox anahtarı değiştiğinde istemcilerin haritayı **yeni anahtarla yeniden
 * kurabilmesi** için gereklidir; aksi hâlde değişiklik ancak sayfa yenilenince
 * etkili olurdu. Yayın AFTER_COMMIT — geri alınan bir değişiklik yayılmaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigRealtimeBroadcaster {

    public static final String CONFIG_TOPIC = "/topic/config";

    private final ClusterBroadcaster broadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfigChanged(ClientConfigChangedEvent event) {
        broadcaster.broadcast(CONFIG_TOPIC, Map.of(
                "type", "CONFIG_CHANGED",
                "key", event.key()));
        log.info("Yapılandırma değişikliği yayınlandı: {}", event.key());
    }
}
