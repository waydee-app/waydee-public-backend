package com.waydee.geo.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.geo.api.dto.GeoDtos.CountryRequest;
import com.waydee.geo.api.dto.GeoDtos.CountryResponse;
import com.waydee.geo.api.dto.GeoDtos.DistrictRequest;
import com.waydee.geo.api.dto.GeoDtos.DistrictResponse;
import com.waydee.geo.api.dto.GeoDtos.ProvinceRequest;
import com.waydee.geo.api.dto.GeoDtos.ProvinceResponse;
import com.waydee.geo.domain.Country;
import com.waydee.geo.domain.District;
import com.waydee.geo.domain.Province;
import com.waydee.geo.infrastructure.CountryRepository;
import com.waydee.geo.infrastructure.DistrictRepository;
import com.waydee.geo.infrastructure.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin bölge yönetimi: ülke / il / ilçe CRUD + fiyat tanımlama + pasife alma.
 */
@Service
@RequiredArgsConstructor
public class RegionService {

    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final AuditRecorder auditRecorder;

    // ------------------------------------------------------------ countries

    @Transactional(readOnly = true)
    public List<CountryResponse> listCountries() {
        return countryRepository.findAllByOrderByNameAsc().stream().map(CountryResponse::from).toList();
    }

    @Transactional
    public CountryResponse createCountry(CountryRequest request, AuthenticatedUser actor) {
        String code = request.code().toUpperCase(Locale.ROOT);
        if (countryRepository.existsByCode(code)) {
            throw new ApiException(ErrorCode.CONFLICT, "Bu ülke kodu zaten kayıtlı: " + code);
        }
        Country country = countryRepository.save(new Country(code, request.name().trim(),
                request.lng(), request.lat(), request.radiusKm(),
                request.defaultPricePerKm2(), request.currency()));
        audit(actor, "COUNTRY_CREATED", "COUNTRY", country.getId(), Map.of("name", country.getName()));
        return CountryResponse.from(country);
    }

    @Transactional
    public CountryResponse updateCountry(UUID id, CountryRequest request, AuthenticatedUser actor) {
        Country country = countryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Ülke bulunamadı"));
        country.setName(request.name().trim());
        country.setDefaultPricePerKm2(request.defaultPricePerKm2());
        country.setCurrency(request.currency());
        country.relocate(request.lng(), request.lat(), request.radiusKm());
        if (request.active() != null) {
            country.setActive(request.active());
        }
        audit(actor, "COUNTRY_UPDATED", "COUNTRY", country.getId(),
                Map.of("name", country.getName(), "active", country.isActive()));
        return CountryResponse.from(country);
    }

    // ------------------------------------------------------------ provinces

    @Transactional(readOnly = true)
    public List<ProvinceResponse> listProvinces(UUID countryId) {
        List<Province> provinces = countryId != null
                ? provinceRepository.findByCountryIdOrderByNameAsc(countryId)
                : provinceRepository.findAllByOrderByNameAsc();
        return provinces.stream().map(ProvinceResponse::from).toList();
    }

    @Transactional
    public ProvinceResponse createProvince(ProvinceRequest request, AuthenticatedUser actor) {
        Country country = countryRepository.findById(request.countryId())
                .orElseThrow(() -> ApiException.notFound("Ülke bulunamadı"));
        if (provinceRepository.existsByCountryIdAndNameIgnoreCase(country.getId(), request.name().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Bu ülkede aynı isimde bir il var");
        }
        Province province = provinceRepository.save(new Province(country, request.name().trim(),
                request.lng(), request.lat(), request.radiusKm(), request.pricePerKm2()));
        audit(actor, "PROVINCE_CREATED", "PROVINCE", province.getId(), Map.of("name", province.getName()));
        return ProvinceResponse.from(province);
    }

    @Transactional
    public ProvinceResponse updateProvince(UUID id, ProvinceRequest request, AuthenticatedUser actor) {
        Province province = provinceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("İl bulunamadı"));
        province.setName(request.name().trim());
        province.setPricePerKm2(request.pricePerKm2());
        province.relocate(request.lng(), request.lat(), request.radiusKm());
        if (request.active() != null) {
            province.setActive(request.active());
        }
        audit(actor, "PROVINCE_UPDATED", "PROVINCE", province.getId(),
                Map.of("name", province.getName(), "active", province.isActive()));
        return ProvinceResponse.from(province);
    }

    // ------------------------------------------------------------ districts

    @Transactional(readOnly = true)
    public List<DistrictResponse> listDistricts(UUID provinceId) {
        List<District> districts = provinceId != null
                ? districtRepository.findByProvinceIdOrderByNameAsc(provinceId)
                : districtRepository.findAllByOrderByNameAsc();
        return districts.stream().map(DistrictResponse::from).toList();
    }

    @Transactional
    public DistrictResponse createDistrict(DistrictRequest request, AuthenticatedUser actor) {
        Province province = provinceRepository.findById(request.provinceId())
                .orElseThrow(() -> ApiException.notFound("İl bulunamadı"));
        if (districtRepository.existsByProvinceIdAndNameIgnoreCase(province.getId(), request.name().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "Bu ilde aynı isimde bir ilçe var");
        }
        District district = districtRepository.save(new District(province, request.name().trim(),
                request.lng(), request.lat(), request.radiusKm(), request.pricePerKm2()));
        audit(actor, "DISTRICT_CREATED", "DISTRICT", district.getId(), Map.of("name", district.getName()));
        return DistrictResponse.from(district);
    }

    @Transactional
    public DistrictResponse updateDistrict(UUID id, DistrictRequest request, AuthenticatedUser actor) {
        District district = districtRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("İlçe bulunamadı"));
        district.setName(request.name().trim());
        district.setPricePerKm2(request.pricePerKm2());
        district.relocate(request.lng(), request.lat(), request.radiusKm());
        if (request.active() != null) {
            district.setActive(request.active());
        }
        audit(actor, "DISTRICT_UPDATED", "DISTRICT", district.getId(),
                Map.of("name", district.getName(), "active", district.isActive()));
        return DistrictResponse.from(district);
    }

    private void audit(AuthenticatedUser actor, String action, String entityType, UUID entityId,
                       Map<String, Object> detail) {
        auditRecorder.record(actor.id(), actor.username(), action, entityType, entityId.toString(), detail, null);
    }
}
