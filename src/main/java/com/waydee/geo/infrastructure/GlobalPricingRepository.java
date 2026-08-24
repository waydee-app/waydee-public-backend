package com.waydee.geo.infrastructure;

import com.waydee.geo.domain.GlobalPricing;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tek satırlık küresel taban fiyat ({@code id = 1}). */
public interface GlobalPricingRepository extends JpaRepository<GlobalPricing, Short> {
}
