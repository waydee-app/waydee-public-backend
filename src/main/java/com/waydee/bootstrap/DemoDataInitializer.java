package com.waydee.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Açılışta yalnızca yönetici hesabını garanti eder. Demo verisi (harita üzerinde
 * önceden oluşan işaretli bölgeler/alanlar) artık üretilmez — temiz kurulumda
 * harita boş açılır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final SeedService seedService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedService.ensureAdmin();
        } catch (Exception ex) {
            // Bootstrap hatası uygulamanın açılmasını engellememeli; sorun loglanır.
            log.error("Yönetici bootstrap işlemi başarısız oldu", ex);
        }
    }
}
