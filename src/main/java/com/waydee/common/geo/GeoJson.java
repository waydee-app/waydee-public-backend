package com.waydee.common.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JTS geometrilerini GeoJSON uyumlu Map yapılarına çevirir.
 * Mapbox GL doğrudan bu çıktıyı tüketir.
 */
public final class GeoJson {

    private GeoJson() {
    }

    public static Map<String, Object> polygon(Polygon polygon) {
        Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();
        List<double[]> ring = new ArrayList<>(coordinates.length);
        for (Coordinate c : coordinates) {
            ring.add(new double[]{round(c.x), round(c.y)});
        }
        return Map.of("type", "Polygon", "coordinates", List.of(ring));
    }

    public static Map<String, Object> point(Point point) {
        return Map.of("type", "Point", "coordinates", new double[]{round(point.getX()), round(point.getY())});
    }

    public static Map<String, Object> feature(String id, Map<String, Object> geometry, Map<String, Object> properties) {
        return Map.of("type", "Feature", "id", id, "geometry", geometry, "properties", properties);
    }

    public static Map<String, Object> featureCollection(List<Map<String, Object>> features) {
        return Map.of("type", "FeatureCollection", "features", features);
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
