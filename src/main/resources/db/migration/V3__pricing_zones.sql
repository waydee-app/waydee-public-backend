-- WAYDEE V3 — dinamik fiyatlandırma bölgeleri
-- Admin haritada serbest poligon çizip o alana özel km² fiyatı tanımlar.
-- Çözümleme sırası: pricing_zones (öncelik DESC) → districts → provinces → countries.

CREATE TABLE pricing_zones (
    id            UUID PRIMARY KEY,
    name          VARCHAR(100)            NOT NULL,
    description   VARCHAR(300),
    boundary      geometry(Polygon, 4326) NOT NULL,
    area_km2      NUMERIC(14, 4)          NOT NULL,
    price_per_km2 NUMERIC(12, 2)          NOT NULL,
    currency      VARCHAR(3)              NOT NULL,
    priority      INTEGER                 NOT NULL DEFAULT 100,
    active        BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ             NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT                  NOT NULL DEFAULT 0,
    CONSTRAINT ck_pricing_zones_price CHECK (price_per_km2 >= 0),
    CONSTRAINT ck_pricing_zones_priority CHECK (priority BETWEEN 0 AND 10000)
);

CREATE INDEX idx_pricing_zones_boundary ON pricing_zones USING GIST (boundary);
CREATE INDEX idx_pricing_zones_lookup ON pricing_zones (active, priority DESC);

-- Satın alma anında hangi fiyat bölgesinin uygulandığı iz olarak saklanır.
ALTER TABLE territories
    ADD COLUMN pricing_zone_id UUID REFERENCES pricing_zones (id) ON DELETE SET NULL;
