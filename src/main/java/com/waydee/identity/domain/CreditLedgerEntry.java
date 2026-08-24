package com.waydee.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * <b>Kredi defteri satırı</b> (V45) — <b>değişmezdir</b>.
 *
 * <p>Faturayla aynı ilke: yazıldıktan sonra düzeltilmez, yanlışsa <b>ters
 * kayıt</b> atılır. Bu yüzden sınıfta hiçbir setter yoktur.
 *
 * <p>🔴 {@link #refKey} <b>istismarı kapatan alandır</b> ve veritabanında
 * <b>UNIQUE</b>'tir:
 * <ul>
 *   <li>{@code plan:<userId>:<planBitişi>} — aynı abonelik dönemi ikinci kez
 *       yüklenemez. Polar yenilemede {@code order.paid} olayını <b>aynı
 *       rezervasyon kimliğiyle</b> gönderiyor (78. tur) ve webhook'lar genel
 *       olarak "en az bir kez" teslim edilir; uygulama katmanındaki bir
 *       {@code if} yarış durumunda yetmez.</li>
 *   <li>{@code refund:<generationId>} — başarısız bir üretim ikinci kez iade
 *       edilemez.</li>
 * </ul>
 */
@Entity
@Table(name = "credit_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLedgerEntry {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Pozitif = yükleme/iade, negatif = harcama. <b>Sıfır olamaz.</b> */
    @Column(name = "delta", nullable = false)
    private int delta;

    /** Hareketten <b>sonraki</b> bakiye — geçmişi yeniden hesaplamadan okumak için. */
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private CreditReason reason;

    @Column(name = "note", length = 200)
    private String note;

    /** Tekrarı engelleyen iş anahtarı; tekrarlanabilir hareketlerde {@code null}. */
    @Column(name = "ref_key", length = 120)
    private String refKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CreditLedgerEntry(UUID userId, int delta, int balanceAfter,
                             CreditReason reason, String note, String refKey) {
        this.userId = userId;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.note = note;
        this.refKey = refKey;
        this.createdAt = Instant.now();
    }
}
