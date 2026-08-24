package com.waydee.geo.domain;

import com.waydee.common.geo.GeoUtils;
import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "provinces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Province extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(name = "name", nullable = false, length = 100)
    @Setter
    private String name;

    @Column(name = "center", nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point center;

    @Column(name = "boundary", nullable = false, columnDefinition = "geometry(Polygon,4326)")
    private Polygon boundary;

    @Column(name = "radius_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal radiusKm;

    @Column(name = "price_per_km2", precision = 12, scale = 2)
    @Setter
    private BigDecimal pricePerKm2;

    @Column(name = "active", nullable = false)
    @Setter
    private boolean active;

    public Province(Country country, String name, double lng, double lat,
                    BigDecimal radiusKm, BigDecimal pricePerKm2) {
        this.country = country;
        this.name = name;
        this.pricePerKm2 = pricePerKm2;
        this.active = true;
        relocate(lng, lat, radiusKm);
    }

    public void relocate(double lng, double lat, BigDecimal radiusKm) {
        this.center = GeoUtils.point(lng, lat);
        this.radiusKm = radiusKm;
        this.boundary = GeoUtils.circle(lng, lat, radiusKm.doubleValue() * 1000);
    }
}
