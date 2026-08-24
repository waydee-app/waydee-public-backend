package com.waydee.geo.application;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Bir koordinat için çözülen fiyatlandırma bağlamı.
 * Fiyat önceliği: admin'in çizdiği fiyatlandırma bölgesi → ilçe → il → ülke varsayılanı.
 * pricingZoneId dolu ise fiyat idari hiyerarşiden değil, çizilen bölgeden gelmiştir.
 */
public record ResolvedRegion(
        UUID countryId,
        String countryName,
        UUID provinceId,
        String provinceName,
        UUID districtId,
        String districtName,
        BigDecimal pricePerKm2,
        String currency,
        UUID pricingZoneId,
        String pricingZoneName,
        /**
         * İdari bilgi de çizilmiş bir fiyat bölgesi de yokken kullanılan koordinat
         * etiketi. Küresel taban fiyat katmanı (V27) bu durumu yarattı: artık
         * dünyanın her yeri satılabilir ama her yerin bir <b>adı</b> yok.
         */
        String coordinateLabel
) {

    /**
     * "Burası neresi?" sorusunun cevabı — <b>asla null dönmez.</b>
     *
     * <p>⚠️ Null dönmesi üretimde satın almayı <b>500</b>'e düşürmüştü: küresel
     * taban fiyat açıkken idari katmanı ve fiyat bölgesi olmayan bir noktada
     * (ör. New York) etiket boş kalıyordu. İki ayrı yerde patlıyor:
     * <ol>
     *   <li>denetim kaydındaki {@code Map.of(...)} <b>null değer kabul etmez</b> → NPE;</li>
     *   <li>etiket, ad girilmediğinde bölgenin varsayılan adıdır ve
     *       {@code territories.name} <b>NOT NULL</b>'dır.</li>
     * </ol>
     * Bu yüzden koordinat etiketi bir süs değil, <b>sözleşmenin parçasıdır</b>.
     */
    public String label() {
        String administrative = administrativeLabel();
        if (pricingZoneName != null) {
            return administrative != null ? pricingZoneName + " · " + administrative : pricingZoneName;
        }
        return administrative != null ? administrative : coordinateLabel;
    }

    private String administrativeLabel() {
        if (districtName != null) {
            return districtName + ", " + provinceName;
        }
        if (provinceName != null) {
            return provinceName + ", " + countryName;
        }
        return countryName;
    }

    /**
     * Adı olmayan bir nokta için okunabilir koordinat etiketi (ör. {@code 40.7648, -73.9808}).
     *
     * <p>⚠️ {@link Locale#ROOT} <b>şart</b>: varsayılan yerel ayar Türkçe olduğu için
     * ondalık ayracı virgül olur ve etiket {@code "40,7648, -73,9808"} gibi okunamaz
     * bir hâle gelirdi. Bu değer faturaya <b>kopyalanarak</b> saklandığı için biçim
     * dilden bağımsız olmalıdır — beş dilde de aynı okunur.
     */
    public static String coordinateLabel(double lng, double lat) {
        return String.format(Locale.ROOT, "%.4f, %.4f", lat, lng);
    }
}
