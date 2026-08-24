package com.waydee.social.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trend skorlarını periyodik yeniler.
 *
 * 10 dakikada bir: duyuru şeridi "canlı" hissettirecek kadar sık, ama sıralama
 * her saniye zıplamayacak kadar seyrek. Açılışta bir kez de koşar ki boş bir
 * şeritle karşılaşılmasın.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendingScheduler {

    private final TrendingService trendingService;

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void refresh() {
        run("zamanlanmış");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshOnStartup() {
        run("açılış");
    }

    private void run(String trigger) {
        try {
            trendingService.recompute();
        } catch (Exception e) {
            // Trend hesabı uygulamayı ya da açılışı DÜŞÜRMEZ — şerit boş kalır, o kadar.
            log.error("Trend hesaplaması başarısız ({})", trigger, e);
        }
    }
}
