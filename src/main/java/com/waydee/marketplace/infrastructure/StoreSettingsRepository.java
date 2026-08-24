package com.waydee.marketplace.infrastructure;

import com.waydee.marketplace.domain.StoreSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StoreSettingsRepository extends JpaRepository<StoreSettings, UUID> {

    /**
     * Cadde çizilirken tüm mağazaların ayarı <b>tek sorguda</b> okunur.
     *
     * <p>⚠️ Mağaza başına ayrı sorgu, 40 dükkânlık bir caddede 40 gidiş-dönüş
     * demekti (N+1). Evren ucu zaten tek istekte tüm sahneyi döndürüyor.
     */
    List<StoreSettings> findByListingIdIn(Collection<UUID> listingIds);
}
