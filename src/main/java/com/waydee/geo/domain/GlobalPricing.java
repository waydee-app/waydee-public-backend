package com.waydee.geo.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Tüm dünyada geçerli taban fiyat — <b>haritada çizilmeyen</b> katman.
 *
 * <p>⚠️ <b>Tek satır</b> ({@code id = 1}, DB'de CHECK ile zorlanır). Birden
 * çok küresel taban "hangisi geçerli?" sorusunu doğururdu.
 *
 * <p>⚠️ Fiyat <b>birimiyle birlikte</b> saklanır: yönetici "metrekaresi 0,05 TL"
 * demek isteyebilir, bunu km² cinsinden 50.000 yazmak zorunda kalmamalı.
 * Çözümleme her zaman km²'ye normalize eder ({@link #pricePerKm2()}).
 */
@Entity
@Table(name = "global_pricing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlobalPricing extends AuditableEntity {

    /** Sabit birincil anahtar — tablo tek satırlıdır. */
    public static final short SINGLETON_ID = 1;

    /** Metrekare → km² çarpanı. */
    private static final BigDecimal M2_PER_KM2 = new BigDecimal("1000000");

    @Id
    @Column(name = "id")
    private Short id = SINGLETON_ID;

    @Column(name = "price", nullable = false, precision = 18, scale = 6)
    @Setter
    private BigDecimal price = BigDecimal.ZERO;

    /** {@code M2} ya da {@code KM2}. */
    @Column(name = "unit", nullable = false, length = 8)
    @Setter
    private String unit = "M2";

    @Column(name = "currency", nullable = false, length = 3)
    @Setter
    private String currency = "TRY";

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active;

    /**
     * Fiyatın km² cinsinden karşılığı — çözümleme hep bu birimle çalışır.
     *
     * <p>⚠️ Karşılaştırma km² üzerinden yapılmalı: "metrekare 0,05" ile
     * "km² 30.000" arasında hangisinin pahalı olduğu ancak aynı birime
     * çevrilince söylenebilir (0,05 m² = 50.000 km², yani daha pahalı).
     */
    public BigDecimal pricePerKm2() {
        return "M2".equals(unit) ? price.multiply(M2_PER_KM2) : price;
    }

    /** Katman gerçekten fiyat üretiyor mu (kapalı ya da sıfır ise hayır). */
    public boolean isEffective() {
        return active && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
