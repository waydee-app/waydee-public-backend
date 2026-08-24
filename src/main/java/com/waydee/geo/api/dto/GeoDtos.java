package com.waydee.geo.api.dto;

import com.waydee.geo.domain.Country;
import com.waydee.geo.domain.District;
import com.waydee.geo.domain.Province;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public final class GeoDtos {

    private GeoDtos() {
    }

    public record CountryRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "Ülke kodu 2 harf olmalı (ISO 3166-1)")
            String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("1") @DecimalMax("5000") BigDecimal radiusKm,
            @NotNull @PositiveOrZero BigDecimal defaultPricePerKm2,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "Para birimi 3 harfli kod olmalı") String currency,
            Boolean active
    ) {
    }

    public record ProvinceRequest(
            @NotNull UUID countryId,
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("1") @DecimalMax("1000") BigDecimal radiusKm,
            @PositiveOrZero BigDecimal pricePerKm2,
            Boolean active
    ) {
    }

    public record DistrictRequest(
            @NotNull UUID provinceId,
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("0.5") @DecimalMax("500") BigDecimal radiusKm,
            @PositiveOrZero BigDecimal pricePerKm2,
            Boolean active
    ) {
    }

    public record CountryResponse(
            UUID id, String code, String name, double lng, double lat,
            BigDecimal radiusKm, BigDecimal defaultPricePerKm2, String currency, boolean active
    ) {
        public static CountryResponse from(Country c) {
            return new CountryResponse(c.getId(), c.getCode(), c.getName(),
                    c.getCenter().getX(), c.getCenter().getY(),
                    c.getRadiusKm(), c.getDefaultPricePerKm2(), c.getCurrency(), c.isActive());
        }
    }

    public record ProvinceResponse(
            UUID id, UUID countryId, String countryName, String name, double lng, double lat,
            BigDecimal radiusKm, BigDecimal pricePerKm2, BigDecimal effectivePricePerKm2,
            String currency, boolean active
    ) {
        public static ProvinceResponse from(Province p) {
            Country c = p.getCountry();
            BigDecimal effective = p.getPricePerKm2() != null ? p.getPricePerKm2() : c.getDefaultPricePerKm2();
            return new ProvinceResponse(p.getId(), c.getId(), c.getName(), p.getName(),
                    p.getCenter().getX(), p.getCenter().getY(),
                    p.getRadiusKm(), p.getPricePerKm2(), effective, c.getCurrency(), p.isActive());
        }
    }

    public record DistrictResponse(
            UUID id, UUID provinceId, String provinceName, String countryName, String name,
            double lng, double lat, BigDecimal radiusKm, BigDecimal pricePerKm2,
            BigDecimal effectivePricePerKm2, String currency, boolean active
    ) {
        public static DistrictResponse from(District d) {
            Province p = d.getProvince();
            Country c = p.getCountry();
            BigDecimal effective = d.getPricePerKm2() != null ? d.getPricePerKm2()
                    : p.getPricePerKm2() != null ? p.getPricePerKm2()
                    : c.getDefaultPricePerKm2();
            return new DistrictResponse(d.getId(), p.getId(), p.getName(), c.getName(), d.getName(),
                    d.getCenter().getX(), d.getCenter().getY(),
                    d.getRadiusKm(), d.getPricePerKm2(), effective, c.getCurrency(), d.isActive());
        }
    }
}
