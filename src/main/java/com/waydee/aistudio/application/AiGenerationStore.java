package com.waydee.aistudio.application;

import com.waydee.aistudio.domain.AiGeneration;
import com.waydee.aistudio.infrastructure.AiGenerationRepository;
import com.waydee.identity.application.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Üretim satırının <b>durum yazıları</b> (V45).
 *
 * <h3>🔴 Neden AYRI bir bean</h3>
 * <p>Bu metotlar {@link AiGenerationRunner} içinde de yazılabilirdi — ve
 * <b>sessizce transaction'sız koşarlardı</b>. Spring'in {@code @Transactional}
 * desteği proxy tabanlıdır: aynı sınıfın bir metodunu {@code this.} ile çağırmak
 * proxy'yi atlar. Hata vermez, yalnız işlem sınırı hiç açılmaz; satır bazen
 * yazılır bazen yazılmazdı. (Aynı tuzağın {@code @Async} hâli
 * {@code AiStudioService} içinde anlatılıyor.)
 *
 * <h3>⚠️ Neden her yazı KENDİ transaction'ında</h3>
 * <p>Tek bir uzun transaction, 40 saniyelik sağlayıcı çağrısı boyunca bir
 * veritabanı bağlantısı tutardı. Hikari havuzu görev başına 20 bağlantı; birkaç
 * eşzamanlı üretim tüm uygulamayı bekletirdi.
 */
@Service
@RequiredArgsConstructor
public class AiGenerationStore {

    private final AiGenerationRepository generationRepository;
    private final CreditService creditService;

    /**
     * Satırı okuyup <b>düz bir kopyaya</b> çevirir.
     *
     * <p>🔴 Entity arka plan işine taşınmaz: {@code inputMediaIds} tembel bir
     * koleksiyondur ve transaction kapandıktan sonra okunması
     * {@code LazyInitializationException} atardı. Transaction'ı ağ çağrıları
     * boyunca açık tutmak da seçenek değil (bağlantı havuzu 20).
     *
     * <p>⚠️ {@code paramsJson} <b>çözümlenmeden</b> taşınır: çözümleme hatası
     * da bir üretim hatasıdır ve koşucunun iade eden {@code try} bloğunun
     * <b>içinde</b> olmalıdır. Burada çözümlenseydi, bozuk bir satır krediyi
     * iade etmeden patlardı.
     */
    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID generationId) {
        AiGeneration g = generationRepository.findById(generationId).orElse(null);
        return g == null ? null : new Snapshot(g.getUserId(), g.getPrompt(),
                List.copyOf(g.getInputMediaIds()), g.getCreditCost(), g.getParamsJson());
    }

    /** Ağ çağrıları boyunca taşınan, transaction'dan bağımsız düz kopya. */
    public record Snapshot(UUID userId, String prompt, List<UUID> inputMediaIds,
                           int creditCost, String paramsJson) {
    }

    @Transactional
    public void markRunning(UUID id, String providerRequestId) {
        generationRepository.findById(id).ifPresent(g -> g.markRunning(providerRequestId));
    }

    @Transactional
    public void markSucceeded(UUID id, UUID mediaId) {
        generationRepository.findById(id).ifPresent(g -> g.markSucceeded(mediaId));
    }

    /**
     * Başarısızlık + <b>iade</b>.
     *
     * <p>🔴 İade en fazla bir kez yapılır ve koruma <b>iki katlıdır</b>:
     * satırdaki {@code refunded} bayrağı (ucuz, sıcak yol) ve defterdeki
     * {@code refund:<id>} <b>tekil</b> anahtarı (kesin, veritabanı seviyesinde).
     * Tek başına bayrak yarış durumunda iki kez okunabilirdi.
     */
    @Transactional
    public void failAndRefund(UUID id, int cost, String message) {
        AiGeneration generation = generationRepository.findById(id).orElse(null);
        if (generation == null) {
            return;
        }
        generation.markFailed(message);
        if (!generation.isRefunded()) {
            generation.markRefunded();
            creditService.refund(generation.getUserId(), cost, "refund:" + id, "Üretim başarısız");
        }
    }

    /** Büyütme başarısız olduğunda yalnız <b>aradaki fark</b> iade edilir. */
    @Transactional
    public void refundUpscaleDelta(UUID id, UUID userId) {
        int delta = CreditCost.BASE_HIGH - CreditCost.BASE_STANDARD;
        creditService.refund(userId, delta, "refund-upscale:" + id, "Büyütme yapılamadı");
    }
}
