package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Bir bölge (profil) görüntüleme kaydı — raporlama için (kim, ne zaman). */
@Entity
@Table(name = "territory_views")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryView {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "territory_id", nullable = false)
    private UUID territoryId;

    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;

    /**
     * Görüntülemenin <b>UTC günü</b> (V54).
     *
     * <p>🔴 {@code (territory_id, viewer_id, view_day)} veritabanında
     * <b>TEKİLDİR</b>: aynı kişi aynı gün bir kez sayılır. Kural uygulamada
     * değil şemada duruyor çünkü uygulama seviyesindeki bir "var mı" kontrolü
     * eşzamanlı iki sekmede yarışır ve sessizce iki satır yazar.
     *
     * <p>⚠️ Gün <b>UTC</b>'dir: ziyaretçi ile sahibin saat dilimi farklı
     * olabilir ve "kimin günü?" sorusunun tek tutarlı cevabı yok. Rapor da
     * UTC gününe göre gruplanıyor.
     */
    @Column(name = "view_day", nullable = false)
    private java.time.LocalDate viewDay;

    public TerritoryView(UUID territoryId, UUID viewerId) {
        this.territoryId = territoryId;
        this.viewerId = viewerId;
        this.viewedAt = Instant.now();
        this.viewDay = this.viewedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
    }
}
