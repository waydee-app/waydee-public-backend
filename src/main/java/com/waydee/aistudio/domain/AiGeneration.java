package com.waydee.aistudio.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <b>Bir yapay zekâ görsel üretimi</b> (V45).
 *
 * <p>🔴 <b>Satır dış servise gitmeden ÖNCE yazılır.</b> Kredi düşümü ile
 * sağlayıcı çağrısı arasında hiçbir kayıt olmasaydı, çağrının ortasında düşen
 * bir sunucudan sonra kullanıcının kredisi gitmiş, karşılığında hiçbir iz
 * kalmamış olurdu — ne sonuç, ne iade edilecek bir kayıt.
 *
 * <p>⚠️ {@link #paramsJson} bir <b>form durumudur</b>, sorgulanmaz: kullanıcı
 * "aynı ayarlarla tekrar üret" diyebilsin ve destek "hangi ayarlarla çıktı"
 * sorusunu yanıtlayabilsin diye saklanır. Bu yüzden kolonlara açılmadı —
 * ayarlar sık değişecek ve her değişiklik bir migration gerektirirdi.
 */
@Entity
@Table(name = "ai_generations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiGeneration {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private AiGenerationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiGenerationStatus status;

    /**
     * Sunucuda hesaplanan maliyet.
     *
     * <p>🔴 İstemciden <b>asla</b> alınmaz. Alınsaydı bir kullanıcı isteğe
     * {@code "creditCost": 1} yazıp bir kredilik üretim yapabilirdi — kredi
     * ekonomilerindeki en yaygın açık budur.
     */
    @Column(name = "credit_cost", nullable = false)
    private int creditCost;

    @Column(name = "refunded", nullable = false)
    private boolean refunded;

    @Column(name = "params_json", nullable = false, columnDefinition = "text")
    private String paramsJson;

    /** Modele giden nihai istem — "neden böyle çıktı"nın tek yanıtı. */
    @Column(name = "prompt", columnDefinition = "text")
    private String prompt;

    @Column(name = "provider_request_id", length = 80)
    private String providerRequestId;

    /**
     * Sonuç görselinin medya kimliği.
     *
     * <p>⚠️ Sağlayıcının döndürdüğü adres <b>saklanmaz</b>: birkaç saat sonra
     * ölür ve galeri kırık görsellerle dolardı. Baytlar indirilip <b>kendi
     * depomuza</b> yazılır; böylece gönderi/hikâye akışına da girebilir.
     */
    @Column(name = "result_media_id")
    private UUID resultMediaId;

    @Column(name = "error_message", length = 300)
    private String errorMessage;

    /**
     * Girdi ürün görselleri — <b>sıra korunur</b>.
     *
     * <p>⚠️ Sıra anlamlıdır: istem ürünlere "1. görsel, 2. görsel" diye atıf
     * yapıyor ve sağlayıcıya da aynı sırayla gönderiliyor. Sırasız bir küme
     * kullanılsaydı aynı ayarlarla iki üretim farklı sonuç verirdi.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ai_generation_inputs", joinColumns = @JoinColumn(name = "generation_id"))
    @OrderColumn(name = "position")
    @Column(name = "media_id", nullable = false)
    private List<UUID> inputMediaIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AiGeneration(UUID userId, AiGenerationKind kind, int creditCost,
                        String paramsJson, String prompt, List<UUID> inputMediaIds) {
        this.userId = userId;
        this.kind = kind;
        this.status = AiGenerationStatus.QUEUED;
        this.creditCost = creditCost;
        this.paramsJson = paramsJson;
        this.prompt = prompt;
        this.inputMediaIds = new ArrayList<>(inputMediaIds);
        this.createdAt = Instant.now();
    }

    public void markRunning(String providerRequestId) {
        this.status = AiGenerationStatus.RUNNING;
        this.providerRequestId = providerRequestId;
    }

    public void markSucceeded(UUID mediaId) {
        this.status = AiGenerationStatus.SUCCEEDED;
        this.resultMediaId = mediaId;
        this.completedAt = Instant.now();
    }

    /**
     * ⚠️ Hata mesajı <b>kırpılır</b>: sağlayıcının gövdesi bazen kilobaytlarca
     * JSON döndürüyor ve kolon 300 karakter. Kırpmadan yazmak, üretim
     * ortamında sessiz bir {@code DataIntegrityViolation} demekti.
     */
    public void markFailed(String message) {
        this.status = AiGenerationStatus.FAILED;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 300));
        this.completedAt = Instant.now();
    }

    /** İade edildi olarak işaretler; ikinci iadeyi engelleyen iki katmandan biri. */
    public void markRefunded() {
        this.refunded = true;
    }
}
