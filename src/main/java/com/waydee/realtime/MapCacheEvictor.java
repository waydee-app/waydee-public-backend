package com.waydee.realtime;

import com.waydee.common.config.CacheConfig;
import com.waydee.geo.application.event.PricingZoneChangedEvent;
import com.waydee.territory.application.event.TerritoryPurchasedEvent;
import com.waydee.territory.application.event.TerritoryRemovedEvent;
import com.waydee.territory.application.event.TerritoryStyleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Harita önbelleklerini, haritayı değiştiren olaylarda boşaltır.
 *
 * <p>Bu sınıf {@link TerritoryRealtimeBroadcaster} ve
 * {@link RegionRealtimeBroadcaster} ile <b>aynı olayları</b> dinler: onlar
 * değişikliği bağlı istemcilere iter, bu ise sunucudaki GeoJSON önbelleğini
 * geçersiz kılar. İkisi birlikte olmadan biri eksik kalır — WebSocket ile
 * haritası anında güncellenen kullanıcı sayfayı yenilediğinde bayat önbellekten
 * <b>eski hâli</b> geri alırdı.
 *
 * <p>🔴 {@code AFTER_COMMIT}: önbellek, veri veritabanına <b>yazıldıktan sonra</b>
 * boşaltılır. Erken boşaltmak daha kötüsünü yapardı — henüz commit edilmemiş
 * hâli okuyup önbelleğe geri koyar, yani bayatlığı kalıcılaştırırdı.
 *
 * <p>Konumu {@code realtime} modülüdür çünkü {@code common} alan modüllerini
 * tanımaz; bu, oradaki yayıncılarla aynı "domain olayı → altyapı" adaptör rolüdür.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapCacheEvictor {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerritoryPurchased(TerritoryPurchasedEvent event) {
        evictTerritories();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerritoryStyleChanged(TerritoryStyleChangedEvent event) {
        evictTerritories();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTerritoryRemoved(TerritoryRemovedEvent event) {
        evictTerritories();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPricingZoneChanged(PricingZoneChangedEvent event) {
        clear(CacheConfig.MAP_REGIONS);
    }

    private void evictTerritories() {
        clear(CacheConfig.MAP_TERRITORIES);
        clear(CacheConfig.MAP_TERRITORIES_PUBLIC);
    }

    /**
     * Redis düşükken de sessiz kalır: önbellek boşaltılamazsa en kötü ihtimalle
     * harita 30 saniye bayat kalır — istek düşmemelidir.
     */
    private void clear(String name) {
        try {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException ex) {
            log.warn("Harita önbelleği boşaltılamadı ({}): {}", name, ex.toString());
        }
    }
}
