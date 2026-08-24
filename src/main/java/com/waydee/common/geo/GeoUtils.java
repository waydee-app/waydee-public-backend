package com.waydee.common.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class GeoUtils {

    public static final int SRID = 4326;
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), SRID);
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    private static final int CIRCLE_STEPS = 64;

    private GeoUtils() {
    }

    public static Point point(double lng, double lat) {
        return FACTORY.createPoint(new Coordinate(lng, lat));
    }

    /**
     * Merkez + yarıçaptan yaklaşık geodezik daire poligonu üretir
     * (frontend'deki turf.circle ile aynı yaklaşım).
     */
    public static Polygon circle(double lng, double lat, double radiusM) {
        Coordinate[] ring = new Coordinate[CIRCLE_STEPS + 1];
        double latRad = Math.toRadians(lat);
        double metersPerDegreeLng = METERS_PER_DEGREE_LAT * Math.max(Math.cos(latRad), 0.01);
        for (int i = 0; i < CIRCLE_STEPS; i++) {
            double angle = 2 * Math.PI * i / CIRCLE_STEPS;
            double dLat = radiusM * Math.cos(angle) / METERS_PER_DEGREE_LAT;
            double dLng = radiusM * Math.sin(angle) / metersPerDegreeLng;
            ring[i] = new Coordinate(lng + dLng, lat + dLat);
        }
        ring[CIRCLE_STEPS] = ring[0];
        return FACTORY.createPolygon(ring);
    }

    public static BigDecimal circleAreaKm2(double radiusM) {
        double km2 = Math.PI * radiusM * radiusM / 1_000_000.0;
        return BigDecimal.valueOf(km2).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Serbest çizilmiş halkadan (lng/lat çiftleri) kapalı poligon üretir.
     * Kendi kendiyle kesişen çizimler ST_MakeValid yerine JTS buffer(0) ile onarılır.
     */
    public static Polygon polygon(List<double[]> ring) {
        if (ring == null || ring.size() < 3) {
            throw new IllegalArgumentException("Poligon en az 3 nokta içermeli");
        }
        List<Coordinate> coordinates = new ArrayList<>(ring.size() + 1);
        for (double[] point : ring) {
            coordinates.add(new Coordinate(point[0], point[1]));
        }
        Coordinate first = coordinates.get(0);
        Coordinate last = coordinates.get(coordinates.size() - 1);
        if (!first.equals2D(last)) {
            coordinates.add(new Coordinate(first));
        }

        Polygon polygon = FACTORY.createPolygon(coordinates.toArray(Coordinate[]::new));
        if (polygon.isValid()) {
            return polygon;
        }
        Geometry repaired = polygon.buffer(0);
        if (repaired instanceof Polygon fixed && !fixed.isEmpty()) {
            return fixed;
        }
        throw new IllegalArgumentException("Geçersiz poligon çizimi");
    }

    /**
     * Poligonun yaklaşık yüzey alanı (km²). Küçük/orta alanlarda yeterli doğrulukta
     * eşdeğer dikdörtgen izdüşümü kullanılır.
     */
    public static BigDecimal polygonAreaKm2(Polygon polygon) {
        Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();
        double latSum = 0;
        for (Coordinate c : coordinates) {
            latSum += c.y;
        }
        double meanLatRad = Math.toRadians(latSum / coordinates.length);
        double degreeAreaSquared = Math.abs(polygon.getArea());
        double kmPerDegreeLat = METERS_PER_DEGREE_LAT / 1000.0;
        double kmPerDegreeLng = kmPerDegreeLat * Math.max(Math.cos(meanLatRad), 0.01);
        return BigDecimal.valueOf(degreeAreaSquared * kmPerDegreeLat * kmPerDegreeLng)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
