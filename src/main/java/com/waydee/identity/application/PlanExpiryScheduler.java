package com.waydee.identity.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Süresi dolan <b>PRO üyelikleri</b> FREE'ye düşürür (V35).
 *
 * <p>Neden zamanlanmış iş: süre dolumu bir kullanıcı eylemi değildir — kimse
 * hesaba dokunmasa bile üyeliğin bitmesi gerekir. Yalnız okuma sorgusunda
 * süzmek yeterli değil: {@code plan} kolonu PRO kaldığı sürece yönetim
 * listesi, raporlar ve destek ekranı <b>yanlış</b> gösterirdi.
 *
 * <p>⚠️ Kapı yine <b>iki katlı</b>: {@code User#isProActive()} okuma tarafında
 * süresi geçmişi zaten FREE sayar, bu iş de satırı gerçeğe çevirir. Aksi halde
 * süpürme ile dolum arasındaki sürede kullanıcı sınırsız kalırdı.
 *
 * <p>Açılışta bir kez de çalışır: sunucu kapalıyken dolan üyelikler ilk istek
 * gelmeden temizlenmiş olur. (Bölge kirasındaki {@code LeaseExpiryScheduler}
 * ile birebir aynı desen.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanExpiryScheduler {

    private final PlanService planService;

    /** Saat başı (10. dakika) — kira süpürmesiyle aynı dakikaya düşmesin. */
    @Scheduled(cron = "0 10 * * * *")
    public void sweep() {
        run("zamanlanmış");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        run("açılış");
    }

    private void run(String trigger) {
        try {
            int expired = planService.expireLapsedPlans();
            if (expired > 0) {
                log.info("Üyelik süpürmesi ({}): {} hesap FREE'ye düştü", trigger, expired);
            }
        } catch (Exception e) {
            // Süpürme hatası uygulamayı ya da açılışı DÜŞÜRMEZ.
            log.error("Üyelik süpürmesi başarısız ({})", trigger, e);
        }
    }
}
