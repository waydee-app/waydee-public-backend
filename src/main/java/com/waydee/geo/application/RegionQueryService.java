package com.waydee.geo.application;

import com.waydee.common.geo.GeoJson;
import com.waydee.geo.api.dto.PricingZoneDtos;
import com.waydee.geo.domain.Country;
import com.waydee.geo.domain.District;
import com.waydee.geo.domain.PricingZone;
import com.waydee.geo.domain.Province;
import com.waydee.geo.infrastructure.CountryRepository;
import com.waydee.geo.infrastructure.DistrictRepository;
import com.waydee.geo.infrastructure.PricingZoneRepository;
import com.waydee.geo.infrastructure.ProvinceRepository;
import com.waydee.common.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Harita için bölge GeoJSON üretimi. Mapbox bu çıktıyı doğrudan kaynak olarak kullanır.
 */
@Service
@RequiredArgsConstructor
public class RegionQueryService {

    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final PricingZoneRepository pricingZoneRepository;

    /**
     * 🔴 <b>Önbellekli</b> (ölçek analizi K2/C7). Harita ürünün ana ekranıdır;
     * bu metot eskiden her istekte tüm ülke/il/ilçe/fiyat bölgesi kümesini
     * çekip GeoJSON'u Java'da sıfırdan kuruyordu.
     *
     * <p>Parametresiz olduğu için önbellek anahtarı tektir; fiyat bölgesi
     * değişince {@code MapCacheEvictor} boşaltır.
     */
    @Cacheable(cacheNames = CacheConfig.MAP_REGIONS)
    @Transactional(readOnly = true)
    public Map<String, Object> regionsAsGeoJson() {
        List<Map<String, Object>> features = new ArrayList<>();

        // Fiyat bölgeleri idari katmanların üstünde çizilir (fiyatta da öncelikli olduğu için).
        for (PricingZone zone : pricingZoneRepository.findAllByOrderByPriorityDescCreatedAtDesc()) {
            if (zone.isActive()) {
                features.add(PricingZoneDtos.toFeature(zone));
            }
        }

        for (Country country : countryRepository.findAllByOrderByNameAsc()) {
            if (!country.isActive()) {
                continue;
            }
            features.add(feature("country-" + country.getId(), country.getBoundary(), props(
                    country.getId().toString(), "COUNTRY", country.getName(),
                    country.getDefaultPricePerKm2(), country.getCurrency())));
        }
        for (Province province : provinceRepository.findAllByOrderByNameAsc()) {
            if (!province.isActive() || !province.getCountry().isActive()) {
                continue;
            }
            BigDecimal effective = province.getPricePerKm2() != null
                    ? province.getPricePerKm2()
                    : province.getCountry().getDefaultPricePerKm2();
            features.add(feature("province-" + province.getId(), province.getBoundary(), props(
                    province.getId().toString(), "PROVINCE", province.getName(),
                    effective, province.getCountry().getCurrency())));
        }
        for (District district : districtRepository.findAllByOrderByNameAsc()) {
            Province province = district.getProvince();
            if (!district.isActive() || !province.isActive() || !province.getCountry().isActive()) {
                continue;
            }
            BigDecimal effective = district.getPricePerKm2() != null ? district.getPricePerKm2()
                    : province.getPricePerKm2() != null ? province.getPricePerKm2()
                    : province.getCountry().getDefaultPricePerKm2();
            features.add(feature("district-" + district.getId(), district.getBoundary(), props(
                    district.getId().toString(), "DISTRICT", district.getName(),
                    effective, province.getCountry().getCurrency())));
        }

        return GeoJson.featureCollection(features);
    }

    private Map<String, Object> feature(String id, org.locationtech.jts.geom.Polygon boundary,
                                        Map<String, Object> properties) {
        return GeoJson.feature(id, GeoJson.polygon(boundary), properties);
    }

    private Map<String, Object> props(String id, String level, String name,
                                      BigDecimal pricePerKm2, String currency) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", id);
        properties.put("level", level);
        properties.put("name", name);
        properties.put("pricePerKm2", pricePerKm2);
        properties.put("currency", currency);
        return properties;
    }
}
