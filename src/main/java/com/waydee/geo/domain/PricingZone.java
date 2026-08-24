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
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Admin'in harita üzerinde serbest çizdiği fiyatlandırma bölgesi.
 * İdari hiyerarşiden (ilçe/il/ülke) bağımsızdır ve fiyat çözümlemesinde en yüksek önceliğe sahiptir.
 */
@Entity
@Table(name = "pricing_zones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PricingZone extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    @Setter
    private String name;

    @Column(name = "description", length = 300)
    @Setter
    private String description;

    @Column(name = "boundary", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    @Column(name = "area_km2", nullable = false, precision = 14, scale = 4)
    private BigDecimal areaKm2;

    @Column(name = "price_per_km2", nullable = false, precision = 12, scale = 2)
    @Setter
    private BigDecimal pricePerKm2;

    @Column(name = "currency", nullable = false, length = 3)
    @Setter
    private String currency;

    /** Çakışan bölgelerde büyük öncelik kazanır. */
    @Column(name = "priority", nullable = false)
    @Setter
    private int priority;

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active;

    public PricingZone(String name, String description, Polygon boundary, BigDecimal areaKm2,
                       BigDecimal pricePerKm2, String currency, int priority) {
        this.name = name;
        this.description = description;
        this.boundary = boundary;
        this.areaKm2 = areaKm2;
        this.pricePerKm2 = pricePerKm2;
        this.currency = currency;
        this.priority = priority;
        this.active = true;
    }

    public void reshape(Polygon boundary, BigDecimal areaKm2) {
        this.boundary = boundary;
        this.areaKm2 = areaKm2;
    }
}
