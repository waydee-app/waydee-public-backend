package com.waydee.territory.infrastructure;

import com.waydee.territory.domain.StoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreCategoryRepository extends JpaRepository<StoreCategory, UUID> {

    /** Seçim listeleri (popup · ayarlar · harita şeridi) — yalnız aktif olanlar. */
    List<StoreCategory> findByActiveTrueOrderBySortOrderAscNameAsc();

    /** Yönetim listesi — pasifler de görünür, yoksa geri açılamazlardı. */
    List<StoreCategory> findAllByOrderBySortOrderAscNameAsc();

    Optional<StoreCategory> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);
}
