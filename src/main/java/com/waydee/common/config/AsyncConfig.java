package com.waydee.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Async desteği — istek yolundan alınmış hafif arka plan işleri
 * (ör. profil görüntüleme kaydı) sınırlı bir havuzda koşar.
 *
 * @EnableScheduling ayrıca zamanlanmış işleri açar (kiralama süresi süpürmesi).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Varsayılan havuz — <b>kaybı kabul edilemez</b> işler (e-posta).
     *
     * <p>Kuyruk dolarsa işi çağıran thread yapar: sessiz kayıp yok, geri basınç
     * var. Doğrulama e-postasının düşmesi, kullanıcının hesabını hiç
     * açamaması demektir; yavaşlamak kaybetmekten iyidir.
     *
     * <p>⚠️ Adı <b>{@code taskExecutor} olarak kalmalı</b>. Birden fazla
     * {@code Executor} bean'i varken niteleyicisiz {@code @Async} bu ada göre
     * seçim yapar; ad değişirse Spring sessizce {@code SimpleAsyncTaskExecutor}'a
     * düşer ve <b>her çağrı için yeni thread</b> açar.
     */
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("waydee-async-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    /**
     * Ölçüm havuzu — <b>kaybı kabul edilebilir</b> işler (profil görüntüleme
     * kaydı, trafik kaydı).
     *
     * <p>🔴 <b>Neden ayrı bir havuz:</b> üçü de tek havuzu paylaşırken SMTP'nin
     * yavaşlığı kuyruğu doldurabiliyordu; kuyruk dolunca {@code CallerRunsPolicy}
     * devreye girip <b>görüntüleme yazmasını istek thread'inde</b> koşturuyordu.
     * Bu, sıcak GET yolunu bloklamamak için {@code @Async} yapılan işin yük
     * altında tam tersine dönmesi demekti — yani korunmak istenen senaryonun ta
     * kendisi.
     *
     * <p>🔴 Reddedilen iş <b>sessizce atılır</b> ({@code DiscardPolicy}) ve bu
     * bilinçlidir: yoğun anda bir görüntüleme sayacının eksik kalması,
     * kullanıcının sayfasının yavaşlamasından iyidir. Sayaç zaten yaklaşık bir
     * ölçüdür — fatura ya da bakiye değil.
     */
    /**
     * <b>Yapay zekâ üretim havuzu</b> (V45).
     *
     * <p>🔴 <b>Neden üçüncü bir havuz.</b> Diğer ikisinden farkı süredir: bir
     * görsel üretimi <b>6–40 saniye</b> boyunca thread'i elinde tutar (dış
     * servise yapılan yoklamalı çağrı). Bu işi {@code taskExecutor}'a koymak
     * e-postaların arkasında kuyruk oluşturur, {@code analyticsExecutor}'a
     * koymak ise felaket olurdu: orada reddedilen iş <b>sessizce atılıyor</b>
     * ve kullanıcının kredisi düşülmüşken üretim hiç başlamamış olurdu.
     *
     * <p>⚠️ Kuyruk <b>sınırlı</b> (60) ve reddetme politikası <b>AbortPolicy</b>:
     * iş sessizce yok olmasın, gürültülü biçimde reddedilsin.
     * {@code CallerRunsPolicy} kullanılsaydı iş, olayı yayınlayan thread'de —
     * yani commit sonrası istek thread'inde — koşar ve isteği dakikalarca
     * bekletirdi.
     *
     * <p>🔴 <b>Reddedilen işin kredisini kim iade eder?</b> Koşucu değil —
     * reddedilen iş hiç <b>başlamaz</b>, yani onun {@code try/catch}'i devreye
     * girmez. Bu boşluğu {@code AiGenerationSweeper} kapatır: takılı kalan
     * satırları süpürüp iade eder. Aynı süpürme, sunucu yeniden başlatıldığında
     * kuyrukta kalan işleri de kurtarır (havuz bellekte, dağıtımda kaybolur).
     *
     * <p>⚠️ Havuz <b>küçük</b> (4): sağlayıcı kotası ve maliyet asıl darboğaz;
     * yüz eşzamanlı üretim açmanın kimseye faydası yok. Kullanıcı başına
     * eşzamanlılık ayrıca {@code AiStudioService.MAX_ACTIVE} ile sınırlı.
     */
    @Bean
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("waydee-ai-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(60);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor analyticsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("waydee-metric-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1_000);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
