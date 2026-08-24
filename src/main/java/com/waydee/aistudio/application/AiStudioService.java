package com.waydee.aistudio.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waydee.aistudio.api.dto.AiStudioDtos.FashionModelRequest;
import com.waydee.aistudio.api.dto.AiStudioDtos.GenerationResponse;
import com.waydee.aistudio.api.dto.AiStudioDtos.QuoteResponse;
import com.waydee.aistudio.domain.AiGeneration;
import com.waydee.aistudio.domain.AiGenerationKind;
import com.waydee.aistudio.domain.AiGenerationStatus;
import com.waydee.aistudio.infrastructure.AiGenerationRepository;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.identity.application.CreditService;
import com.waydee.social.application.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * <b>Yapay zekâ görsel stüdyosu</b> (V45).
 *
 * <h3>Akışın sırası — her adım bir öncekini korur</h3>
 * <ol>
 *   <li><b>Plan kapısı</b> — ücretsiz hesap giremez.</li>
 *   <li><b>Hız kapıları</b> — aynı anda en fazla {@value #MAX_ACTIVE} açık iş,
 *       saatte en fazla {@value #MAX_PER_HOUR} üretim.</li>
 *   <li><b>Sahiplik</b> — her ürün görseli <b>isteği yapanın</b> olmalı.</li>
 *   <li><b>Maliyet</b> sunucuda hesaplanır.</li>
 *   <li><b>Satır yazılır</b> (dış çağrıdan ÖNCE).</li>
 *   <li><b>Kredi düşülür</b> (atomik).</li>
 *   <li>Arka planda sağlayıcıya gidilir; başarısızlıkta <b>iade</b>.</li>
 * </ol>
 *
 * <p>🔴 5 ve 6'nın sırası bilinçli: satır olmadan düşülen bir kredi, iade
 * edilecek bir kaydı olmayan kayıp bir kredidir. Tersi (önce düş, sonra yaz)
 * de mümkündü ama o zaman satır yazımı başarısız olursa kredi kaybolurdu — bu
 * sıralamada ise kredi düşümü başarısız olursa geriye yalnız <b>QUEUED</b>
 * kalan bir satır kalır ve o satır hiç çalıştırılmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiStudioService {

    /** Aynı anda açık kalabilecek üretim sayısı (kullanıcı başına). */
    public static final int MAX_ACTIVE = 3;

    /**
     * Saatlik üretim tavanı.
     *
     * <p>🔴 Kredi tek başına yeterli bir fren <b>değil</b>: yıllık Premium bir
     * hesabın 120.000 kredisi var ve bunu bir saatte harcamaya kalkarsa hem
     * sağlayıcı kotamızı hem uygulamanın thread havuzunu tüketir. Kredi
     * <b>ekonomik</b>, bu <b>işletimsel</b> bir sınırdır.
     */
    public static final int MAX_PER_HOUR = 40;

    private final AiGenerationRepository generationRepository;
    private final CreditService creditService;
    private final MediaService mediaService;
    private final FashionPromptBuilder promptBuilder;
    private final FalClient falClient;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    /* ------------------------------------------------------------------ okuma */

    /**
     * Maliyet önizlemesi.
     *
     * <p>⚠️ Bakiyeyi de döndürür: arayüz "240 kredi" yazarken düğmeyi de
     * kilitleyebilsin. İki ayrı çağrı olsaydı ikisi arasında bakiye değişebilir
     * ve düğme yanlış durumda kalırdı.
     */
    @Transactional(readOnly = true)
    public QuoteResponse quote(UUID userId, boolean highQuality, int productCount) {
        int cost = CreditCost.of(highQuality, productCount);
        int balance = creditService.summary(userId).balance();
        return new QuoteResponse(cost, balance, balance >= cost);
    }

    @Transactional(readOnly = true)
    public List<GenerationResponse> list(UUID userId, int limit) {
        return generationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(Math.max(limit, 1), 60)))
                .stream()
                .map(GenerationResponse::from)
                .toList();
    }

    /**
     * Tek üretim — arayüz bunu <b>yoklayarak</b> sonucu bekler.
     *
     * <p>🔒 Sahiplik kontrolü şart: kimlik tahmin edilemez olsa da
     * "bulunması zor" güvenlik değildir.
     */
    @Transactional(readOnly = true)
    public GenerationResponse get(UUID userId, UUID generationId) {
        AiGeneration generation = generationRepository.findById(generationId)
                .orElseThrow(() -> ApiException.notFound("Üretim bulunamadı"));
        if (!generation.getUserId().equals(userId)) {
            throw ApiException.forbidden("Bu üretim size ait değil");
        }
        return GenerationResponse.from(generation);
    }

    /* --------------------------------------------------------------- yaratma */

    /**
     * "Fast Model" üretimini <b>başlatır</b> ve hemen döner.
     *
     * <p>⚠️ Yanıt {@code QUEUED} durumundadır; sonuç yoklama ile alınır.
     * Senkron beklemek 6–40 saniye boyunca bir istek thread'ini bloke ederdi.
     */
    @Transactional
    public GenerationResponse createFashionModel(UUID userId, FashionModelRequest request) {
        if (!falClient.isConfigured()) {
            /* ⚠️ Kapı EN BAŞTA: anahtar yokken kredi düşürmek, kullanıcıdan
               bizim yapılandırma eksiğimiz için para almak olurdu. */
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Yapay zekâ stüdyosu şu anda kullanılamıyor. Lütfen daha sonra dene.");
        }
        creditService.assertCanUseAi(userId);
        assertWithinRateLimits(userId);

        List<UUID> products = request.productMediaIds();
        if (products.isEmpty() || products.size() > CreditCost.MAX_PRODUCTS) {
            throw ApiException.badRequest("En az 1, en fazla " + CreditCost.MAX_PRODUCTS + " ürün görseli ekleyebilirsin");
        }
        /* 🔒 IDOR kapısı: başkasının medya kimliğini yazan bir istek, o görseli
           kendi üretiminde kullanabilirdi. Projedeki diğer medya tüketicileri
           de bu kontrolü yapıyor (`updateMe`'deki H3 bulgusu). */
        products.forEach(mediaId -> mediaService.assertOwnedBy(mediaId, userId));

        String prompt = promptBuilder.build(request);
        int cost = CreditCost.of(request.highQuality(), products.size());

        AiGeneration generation = generationRepository.save(new AiGeneration(
                userId, AiGenerationKind.FASHION_MODEL, cost, writeParams(request), prompt, products));

        // Atomik düşüm — yetmezse 402 ve satır QUEUED'da kalır (hiç çalıştırılmaz).
        creditService.spend(userId, cost, "gen:" + generation.getId(), "Fast Model");

        /*
         * 🔴 İş DOĞRUDAN çağrılmaz, OLAY yayınlanır.
         *
         * İki tuzağı birden kapatır:
         *   ① Aynı sınıftan çağrılan bir `@Async` metodu proxy'den geçmez ve
         *      sessizce SENKRON koşar — istek 40 saniye asılı kalırdı.
         *   ② Bu transaction henüz COMMIT edilmedi. Arka plan thread'i satırı
         *      okumaya kalksa **bulamazdı** (kendi bağlantısı, kendi görüşü).
         *      Dinleyici `AFTER_COMMIT` fazında koşar, yani satır kesin oradadır.
         * Projedeki `TerritoryRealtimeBroadcaster` da tam olarak bu deseni
         * kullanıyor ([[02-mimari]]).
         */
        events.publishEvent(new AiGenerationQueuedEvent(generation.getId()));

        return GenerationResponse.from(generation);
    }

    /* ---------------------------------------------------------------- kapılar */

    private void assertWithinRateLimits(UUID userId) {
        long active = generationRepository.countByUserIdAndStatusIn(
                userId, EnumSet.of(AiGenerationStatus.QUEUED, AiGenerationStatus.RUNNING));
        if (active >= MAX_ACTIVE) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Aynı anda en fazla " + MAX_ACTIVE + " görsel üretebilirsin. Mevcut üretimlerin bitsin.");
        }
        long lastHour = generationRepository.countByUserIdAndCreatedAtAfter(
                userId, Instant.now().minus(Duration.ofHours(1)));
        if (lastHour >= MAX_PER_HOUR) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Saatlik üretim sınırına ulaştın. Bir süre sonra tekrar dene.");
        }
    }

    /**
     * Ayarları JSON'a yazar.
     *
     * <p>⚠️ Yazım başarısız olursa üretim <b>durdurulur</b>: parametresiz bir
     * satır, destek talebinde "hangi ayarlarla üretildi" sorusunu yanıtsız
     * bırakır ve "aynı ayarlarla tekrar" özelliği sessizce bozulurdu.
     */
    private String writeParams(FashionModelRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Ayarlar kaydedilemedi");
        }
    }
}
