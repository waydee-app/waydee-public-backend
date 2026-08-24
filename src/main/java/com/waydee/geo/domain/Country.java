package com.waydee.geo.domain;

import com.waydee.common.geo.GeoUtils;
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
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "countries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Country extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "code", nullable = false, length = 2)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    @Setter
    private String name;

    @Column(name = "center", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point center;

    @Column(name = "boundary", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    @Column(name = "radius_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal radiusKm;

    @Column(name = "default_price_per_km2", nullable = false, precision = 12, scale = 2)
    @Setter
    private BigDecimal defaultPricePerKm2;

    @Column(name = "currency", nullable = false, length = 3)
    @Setter
    private String currency;

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active;

    public Country(String code, String name, double lng, double lat, BigDecimal radiusKm,
                   BigDecimal defaultPricePerKm2, String currency) {
        this.code = code.toUpperCase();
        this.name = name;
        this.radiusKm = radiusKm;
        this.defaultPricePerKm2 = defaultPricePerKm2;
        this.currency = currency;
        this.active = true;
        relocate(lng, lat, radiusKm);
    }

    public void relocate(double lng, double lat, BigDecimal radiusKm) {
        this.center = GeoUtils.point(lng, lat);
        this.radiusKm = radiusKm;
        this.boundary = GeoUtils.circle(lng, lat, radiusKm.doubleValue() * 1000);
    }
}
