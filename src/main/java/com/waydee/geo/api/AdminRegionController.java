package com.waydee.geo.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.geo.api.dto.GeoDtos.CountryRequest;
import com.waydee.geo.api.dto.GeoDtos.CountryResponse;
import com.waydee.geo.api.dto.GeoDtos.DistrictRequest;
import com.waydee.geo.api.dto.GeoDtos.DistrictResponse;
import com.waydee.geo.api.dto.GeoDtos.ProvinceRequest;
import com.waydee.geo.api.dto.GeoDtos.ProvinceResponse;
import com.waydee.geo.application.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin · Regions", description = "Ülke / il / ilçe yönetimi ve fiyatlandırma")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRegionController {

    private final RegionService regionService;

    // ---------------- countries

    @Operation(summary = "Ülkeleri listele")
    @GetMapping("/countries")
    public List<CountryResponse> listCountries() {
        return regionService.listCountries();
    }

    @Operation(summary = "Ülke oluştur")
    @PostMapping("/countries")
    public ResponseEntity<CountryResponse> createCountry(@Valid @RequestBody CountryRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(regionService.createCountry(request, actor));
    }

    @Operation(summary = "Ülke güncelle (fiyat, konum, aktiflik)")
    @PutMapping("/countries/{id}")
    public CountryResponse updateCountry(@PathVariable UUID id,
                                         @Valid @RequestBody CountryRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser actor) {
        return regionService.updateCountry(id, request, actor);
    }

    // ---------------- provinces

    @Operation(summary = "İlleri listele")
    @GetMapping("/provinces")
    public List<ProvinceResponse> listProvinces(@RequestParam(required = false) UUID countryId) {
        return regionService.listProvinces(countryId);
    }

    @Operation(summary = "İl oluştur")
    @PostMapping("/provinces")
    public ResponseEntity<ProvinceResponse> createProvince(@Valid @RequestBody ProvinceRequest request,
                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(regionService.createProvince(request, actor));
    }

    @Operation(summary = "İl güncelle (fiyat, konum, aktiflik)")
    @PutMapping("/provinces/{id}")
    public ProvinceResponse updateProvince(@PathVariable UUID id,
                                           @Valid @RequestBody ProvinceRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return regionService.updateProvince(id, request, actor);
    }

    // ---------------- districts

    @Operation(summary = "İlçeleri listele")
    @GetMapping("/districts")
    public List<DistrictResponse> listDistricts(@RequestParam(required = false) UUID provinceId) {
        return regionService.listDistricts(provinceId);
    }

    @Operation(summary = "İlçe oluştur")
    @PostMapping("/districts")
    public ResponseEntity<DistrictResponse> createDistrict(@Valid @RequestBody DistrictRequest request,
                                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(regionService.createDistrict(request, actor));
    }

    @Operation(summary = "İlçe güncelle (fiyat, konum, aktiflik)")
    @PutMapping("/districts/{id}")
    public DistrictResponse updateDistrict(@PathVariable UUID id,
                                           @Valid @RequestBody DistrictRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        return regionService.updateDistrict(id, request, actor);
    }
}
