package com.waydee.aistudio.application;

import com.waydee.aistudio.domain.AiGeneration;
import com.waydee.aistudio.domain.AiGenerationStatus;
import com.waydee.aistudio.infrastructure.AiGenerationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * <b>Takılı kalan üretimleri kurtarır</b> (V45).
 *
 * <h3>🔴 Neden gerekli — koşucunun {@code catch}'i YETMEZ</h3>
 * <p>{@link AiGenerationRunner} yalnız <b>başlayan</b> bir işi kurtarabilir.
 * Üç gerçek senaryoda iş hiç başlamaz ve kredi karşılıksız yanardı:
 * <ol>
 *   <li><b>Havuz kuyruğu doldu</b> → {@code AbortPolicy} işi reddetti. İstisna
 *       {@code AFTER_COMMIT} dinleyicisinde atılır ve Spring onu yalnız
 *       <b>loglar</b>; hiçbir iade tetiklenmez.</li>
 *   <li><b>Dağıtım / yeniden başlatma</b> → havuz kuyruğu <b>bellektedir</b>,
 *       süreçle birlikte kaybolur. AWS'te her push bir yeniden dağıtımdır.</li>
 *   <li><b>Sağlayıcı çağrısı asılı kaldı</b> ve zaman aşımı bile dönmedi.</li>
 * </ol>
 *
 * <p>⚠️ Eşik ({@value #STUCK_MINUTES} dk) sağlayıcı zaman aşımından
 * ({@code waydee.ai.fal.timeout-seconds}, varsayılan 240 sn) <b>belirgin
 * biçimde büyük</b> olmalı. Aksi halde süpürme, hâlâ çalışan bir üretimi
 * "başarısız" ilan eder ve kullanıcı hem krediyi geri alır hem de görseli —
 * yani iş bedava olurdu.
 *
 * <p>⚠️ Süpürme çok task'lı ortamda <b>her task'ta</b> koşar; sorun değil,
 * çünkü iade {@code refund:<id>} tekil anahtarıyla korunuyor ve ikinci deneme
 * hiçbir şey yapmaz. (Projedeki kira ve plan süpürmeleriyle aynı desen.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGenerationSweeper {

    /** Bu kadar dakikadır açık olan bir üretim artık takılmış sayılır. */
    public static final int STUCK_MINUTES = 20;

    private final AiGenerationRepository generationRepository;
    private final AiGenerationStore store;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 2 * 60 * 1000L)
    public void sweep() {
        List<AiGeneration> stuck = generationRepository.findByStatusInAndCreatedAtBefore(
                EnumSet.of(AiGenerationStatus.QUEUED, AiGenerationStatus.RUNNING),
                Instant.now().minus(Duration.ofMinutes(STUCK_MINUTES)));
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("{} takılı yapay zekâ üretimi kurtarılıyor (kredi iade ediliyor)", stuck.size());
        stuck.forEach(g -> store.failAndRefund(g.getId(), g.getCreditCost(),
                "Üretim zamanında tamamlanamadı"));
    }
}
