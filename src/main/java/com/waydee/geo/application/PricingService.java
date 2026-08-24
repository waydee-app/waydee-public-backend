package com.waydee.geo.application;

import com.waydee.geo.domain.Country;
import com.waydee.geo.domain.District;
import com.waydee.geo.domain.PricingZone;
import com.waydee.geo.domain.Province;
import com.waydee.geo.infrastructure.CountryRepository;
import com.waydee.geo.infrastructure.DistrictRepository;
import com.waydee.geo.infrastructure.PricingZoneRepository;
import com.waydee.geo.infrastructure.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final PricingZoneRepository pricingZoneRepository;
    private final com.waydee.geo.infrastructure.GlobalPricingRepository globalPricingRepository;

    /**
     * Verilen noktayı kapsayan bağlamı çözer ve efektif km² fiyatını belirler.
     *
     * <p>Katmanlar: <b>küresel taban</b> · admin'in çizdiği fiyat bölgesi ·
     * ilçe → il → ülke hiyerarşisi. İdari etiketler (ülke/il/ilçe) hangi
     * katman kazanırsa kazansın korunur.
     *
     * <p>⚠️ <b>EN PAHALI KAZANIR.</b> Eskiden sıralama katıydı (zone her zaman
     * hiyerarşiyi eziyordu); artık aday fiyatlar karşılaştırılır ve büyük olan
     * uygulanır. Küresel taban da bir adaydır: dünyanın her yerinde bir fiyat
     * olmasını sağlar ama üstüne çizilen pahalı bir bölge onu geçer.
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedRegion> resolve(double lng, double lat) {
        Optional<ResolvedRegion> administrative = resolveAdministrative(lng, lat);
        Optional<PricingZone> zone = pricingZoneRepository.findActiveContaining(lng, lat);

        Optional<com.waydee.geo.domain.GlobalPricing> global = globalPricing();
        if (zone.isEmpty() && administrative.isEmpty() && global.isEmpty()) {
            return Optional.empty();
        }

        /*
         * ⚠️ FİYAT ile ETİKET ayrı çözülür.
         *
         * Etiket "burası neresi?" sorusunun cevabıdır ve fiyattan bağımsızdır;
         * fiyat ise adaylar arasında EN PAHALI olandır. İkisini birlikte
         * seçmek, küresel taban kazandığında bölge adını kaybettiriyordu
         * (ölçüldü: `regionLabel: null` → arayüzde "Konum çözümleniyor…"
         * takılı kalıyor).
         */
        BigDecimal bestPrice = null;
        String bestCurrency = null;
        boolean zoneWon = false;

        if (zone.isPresent() && zone.get().getPricePerKm2() != null) {
            bestPrice = zone.get().getPricePerKm2();
            bestCurrency = zone.get().getCurrency();
            zoneWon = true;
        }
        if (administrative.isPresent() && administrative.get().pricePerKm2() != null
                && (bestPrice == null || administrative.get().pricePerKm2().compareTo(bestPrice) > 0)) {
            bestPrice = administrative.get().pricePerKm2();
            bestCurrency = administrative.get().currency();
            zoneWon = false;
        }
        if (global.isPresent() && (bestPrice == null || global.get().pricePerKm2().compareTo(bestPrice) > 0)) {
            bestPrice = global.get().pricePerKm2();
            bestCurrency = global.get().getCurrency();
            zoneWon = false;
        }

        if (bestPrice == null) {
            // Yalnız etiket var, fiyat yok — satış yapılamaz.
            return Optional.empty();
        }

        ResolvedRegion labels = administrative.orElse(null);
        return Optional.of(new ResolvedRegion(
                labels != null ? labels.countryId() : null,
                labels != null ? labels.countryName() : null,
                labels != null ? labels.provinceId() : null,
                labels != null ? labels.provinceName() : null,
                labels != null ? labels.districtId() : null,
                labels != null ? labels.districtName() : null,
                bestPrice,
                bestCurrency,
                // ⚠️ Zone kimliği yalnız FİYATI O KAZANDIYSA iz olarak yazılır;
                // ad ise bağlam olduğu için her durumda gösterilir.
                zoneWon ? zone.get().getId() : null,
                zone.map(PricingZone::getName).orElse(null),
                ResolvedRegion.coordinateLabel(lng, lat)));
    }

    /** Küresel taban — tanımlı, açık ve sıfırdan büyükse. */
    private Optional<com.waydee.geo.domain.GlobalPricing> globalPricing() {
        return globalPricingRepository
                .findById(com.waydee.geo.domain.GlobalPricing.SINGLETON_ID)
                .filter(com.waydee.geo.domain.GlobalPricing::isEffective);
    }


    private Optional<ResolvedRegion> resolveAdministrative(double lng, double lat) {
        // Koordinat etiketi burada da taşınır: idari kayıt bulunsa bile adı olmayan
        // bir katmana (ör. adsız ülke) düşme ihtimaline karşı label() hiç null kalmasın.
        String fallback = ResolvedRegion.coordinateLabel(lng, lat);

        Optional<District> district = districtRepository.findActiveContaining(lng, lat);
        if (district.isPresent()) {
            District d = district.get();
            Province p = d.getProvince();
            Country c = p.getCountry();
            BigDecimal price = firstNonNull(d.getPricePerKm2(), p.getPricePerKm2(), c.getDefaultPricePerKm2());
            return Optional.of(new ResolvedRegion(c.getId(), c.getName(), p.getId(), p.getName(),
                    d.getId(), d.getName(), price, c.getCurrency(), null, null, fallback));
        }

        Optional<Province> province = provinceRepository.findActiveContaining(lng, lat);
        if (province.isPresent()) {
            Province p = province.get();
            Country c = p.getCountry();
            BigDecimal price = firstNonNull(p.getPricePerKm2(), c.getDefaultPricePerKm2());
            return Optional.of(new ResolvedRegion(c.getId(), c.getName(), p.getId(), p.getName(),
                    null, null, price, c.getCurrency(), null, null, fallback));
        }

        return countryRepository.findActiveContaining(lng, lat)
                .map(c -> new ResolvedRegion(c.getId(), c.getName(), null, null,
                        null, null, c.getDefaultPricePerKm2(), c.getCurrency(), null, null, fallback));
    }

    private BigDecimal firstNonNull(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalStateException("Fiyat hiyerarşisi boş olamaz");
    }
}
