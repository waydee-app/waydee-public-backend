package com.waydee.marketplace.application;

import com.waydee.common.geo.GeoUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Onaylanan stantları pazar yerinin <b>içine</b> yerleştirir.
 *
 * <p><b>Neden altın açı spirali:</b> stantlar haritada bir arada ama üst üste
 * binmeden durmalı. Denenen alternatifler ve sorunları:
 * <ul>
 *   <li><i>Rastgele nokta</i> — kümeleniyor, bazıları çakışıyor, her yeniden
 *       hesaplamada yer değiştiriyor (kalıcı değil).</li>
 *   <li><i>Izgara</i> — poligon dikdörtgen olmadığında kenarlarda boşluk
 *       kalıyor, sayı arttıkça ızgarayı büyütmek TÜM stantların yerini
 *       değiştiriyor.</li>
 *   <li><i>Altın açı (Vogel) spirali</i> — n'inci nokta yalnız n'e bağlıdır:
 *       yeni stant eklemek eskilerin yerini <b>değiştirmez</b>, dağılım
 *       merkezden dışa doğru düzgün açılır ve yoğunluk her yerde eşittir.</li>
 * </ul>
 *
 * <p>Spiral noktası poligonun dışına düşerse (içbükey şekiller, delikler)
 * o indeks atlanır ve bir sonrakine bakılır; böylece stant her zaman
 * sınırların içinde kalır.
 */
public final class StallPlacement {

    private StallPlacement() {
    }

    /** ~137.5° — altın açı; en homojen dairesel dağılımı verir. */
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

    /** Kenara yapışmasın diye poligon içine doğru güvenlik payı. */
    private static final double EDGE_INSET = 0.86;

    /** Bir indeks için yer bulunamazsa bu kadar deneme sonra merkeze düşülür. */
    private static final int MAX_PROBES = 400;

    /**
     * Yayılma katsayısı: küçük değer stantları hızla kenara iter, büyük değer
     * merkeze toplar. 12 ≈ ilk 30 stant alanın iç %70'ine düzgün dağılır.
     */
    private static final double SPREAD = 12.0;

    /**
     * @param index 0 tabanlı stant sırası (kalıcıdır — stant silinse bile
     *              diğerlerinin indeksi değişmez, yerleri sabit kalır)
     */
    public static Point forIndex(Polygon boundary, int index) {
        PreparedGeometry prepared = PreparedGeometryFactory.prepare(boundary);
        Envelope env = boundary.getEnvelopeInternal();
        double cx = boundary.getCentroid().getX();
        double cy = boundary.getCentroid().getY();
        // Yarı eksenler: enlem/boylam ölçeği farklı olduğu için ayrı ayrı.
        double rx = (env.getWidth() / 2) * EDGE_INSET;
        double ry = (env.getHeight() / 2) * EDGE_INSET;

        int probe = index;
        for (int attempt = 0; attempt < MAX_PROBES; attempt++, probe++) {
            /*
             * Vogel deseni, N'den BAĞIMSIZ normalize edilmiş yarıçapla:
             *     θ(n) = n × altın açı
             *     r(n) = sqrt(n) / sqrt(n + SPREAD)   → [0, 1) arasında, n ile artan
             *
             * Klasik Vogel `sqrt(n/N)` kullanır ama N (toplam stant sayısı) burada
             * sabit değildir; her yeni stantta N büyüyeceği için TÜM noktalar yer
             * değiştirirdi. Paydaya sabit eklemek yarıçapı yalnız n'e bağlar:
             * yeni stant eklemek eskileri oynatmaz.
             */
            double n = probe + 0.5;
            double radius = Math.sqrt(n) / Math.sqrt(n + SPREAD);
            double angle = probe * GOLDEN_ANGLE;
            Point candidate = GeoUtils.point(
                    cx + Math.cos(angle) * radius * rx,
                    cy + Math.sin(angle) * radius * ry);
            if (prepared.contains(candidate)) {
                return candidate;
            }
        }
        // Hiçbir aday tutmadıysa merkez her zaman güvenlidir (poligon geçerliyse).
        return GeoUtils.point(cx, cy);
    }

    /** Toplu üretim — vitrin önizlemesi ve testler için. */
    public static List<Point> forCount(Polygon boundary, int count) {
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(forIndex(boundary, i));
        }
        return points;
    }

    /** Poligonun içinde mi (başvuru sırasında serbest konum verilirse). */
    public static boolean contains(Polygon boundary, double lng, double lat) {
        return boundary.contains(GeoUtils.point(lng, lat));
    }

    /** JTS koordinatı → nokta (yardımcı). */
    static Point toPoint(Coordinate c) {
        return GeoUtils.point(c.x, c.y);
    }
}
