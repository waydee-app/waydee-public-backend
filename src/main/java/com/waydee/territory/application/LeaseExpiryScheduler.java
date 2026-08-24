package com.waydee.territory.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Kirası dolan bölgeleri düzenli olarak EXPIRED'a düşürür.
 *
 * Neden zamanlanmış iş: süre dolumu bir kullanıcı eylemi değildir — kimse
 * o bölgeye dokunmasa bile haritadan inmesi gerekir. Okuma sorgularında
 * "expiresAt &gt; now" süzmek yeterli değildi; durum kalıcı olarak yazılmazsa
 * yenileme, bildirim ve raporlama tarafı süresi dolmuşu göremezdi.
 *
 * Açılışta bir kez de çalışır: sunucu kapalıyken dolan kiralar, ilk istek
 * gelmeden önce temizlenmiş olur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaseExpiryScheduler {

    private final TerritoryService territoryService;

    /** Saat başı — dakika hassasiyeti gerekmiyor, gün bazlı bir iştir. */
    @Scheduled(cron = "0 5 * * * *")
    public void sweep() {
        run("zamanlanmış");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        run("açılış");
    }

    private void run(String trigger) {
        try {
            int expired = territoryService.expireLapsedLeases();
            if (expired > 0) {
                log.info("Kiralama süpürmesi ({}): {} bölge süresi doldu", trigger, expired);
            }
        } catch (Exception e) {
            // Süpürme hatası uygulamayı ya da açılışı DÜŞÜRMEZ.
            log.error("Kiralama süpürmesi başarısız ({})", trigger, e);
        }
    }
}
