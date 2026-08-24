-- V24 · Gerçek ödeme altyapısı (LemonSqueezy) — 2 Ağustos 2026
--
-- ⚠️ NEDEN REZERVASYON GEREKİYOR
-- Sahte ödeme geçidi ANINDA sonuç dönüyordu, bu yüzden satın alma tek bir
-- transaction'da bitiyordu: kilit → çakışma kontrolü → ödeme → insert.
-- Gerçek ödemede kullanıcı sağlayıcının sayfasına gider ve sonuç **dakikalar
-- sonra** webhook ile döner. Bu boşlukta ikinci bir kullanıcı aynı daireyi
-- çizip ödeyebilirdi → iki kişi aynı alana para öder, biri kaybeder.
--
-- Çözüm: ödeme başlatılırken daire GEOMETRİSİYLE BİRLİKTE rezerve edilir.
-- Bekleyen rezervasyonlar çakışma kontrolüne dahil edilir; süresi dolan
-- rezervasyon süpürülür ve alan yeniden serbest kalır.

CREATE TABLE IF NOT EXISTS payment_checkouts (
    id                   UUID PRIMARY KEY,
    user_id              UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- TERRITORY_PURCHASE | TERRITORY_RENEWAL
    kind                 VARCHAR(24)    NOT NULL,
    -- PENDING | PAID | EXPIRED | CANCELLED | FAILED
    status               VARCHAR(16)    NOT NULL,
    provider             VARCHAR(24)    NOT NULL,
    provider_checkout_id VARCHAR(120),
    provider_order_id    VARCHAR(120),
    checkout_url         TEXT,

    amount               NUMERIC(14, 2) NOT NULL,
    currency             VARCHAR(3)     NOT NULL,
    price_per_km2        NUMERIC(14, 2),
    area_km2             NUMERIC(14, 6),

    -- Satın alma niyeti: daire, adı ve görünümü. Ödeme onaylanınca bölge
    -- BU değerlerden üretilir; istemci ikinci kez veri göndermez (aksi halde
    -- ödenen fiyattan farklı bir daire oluşturulabilirdi).
    center               geometry(Point, 4326),
    boundary             geometry(Polygon, 4326),
    radius_m             INT,
    territory_name       VARCHAR(120),
    region_label         VARCHAR(200),
    country_id           UUID,
    province_id          UUID,
    district_id          UUID,
    pricing_zone_id      UUID,
    style                JSONB,
    -- Yenilemede dolu: hangi bölgenin kirası uzatılıyor.
    territory_id         UUID,

    expires_at           TIMESTAMPTZ    NOT NULL,
    paid_at              TIMESTAMPTZ,
    failure_reason       VARCHAR(300),
    created_ip           VARCHAR(45),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_checkouts_kind   CHECK (kind IN ('TERRITORY_PURCHASE', 'TERRITORY_RENEWAL')),
    CONSTRAINT ck_payment_checkouts_status CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED'))
);

-- Çakışma sorgusu (bekleyen rezervasyonlar) sıcak yolda: her satın alma
-- denemesi buraya bakar. Kısmi GIST — yalnız bekleyenler indekslenir.
CREATE INDEX IF NOT EXISTS idx_payment_checkouts_pending_boundary
    ON payment_checkouts USING GIST (boundary) WHERE status = 'PENDING';

-- Süpürme işi ve kullanıcının "bekleyen ödemem var mı" sorgusu.
CREATE INDEX IF NOT EXISTS idx_payment_checkouts_pending_expiry
    ON payment_checkouts (expires_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_payment_checkouts_user
    ON payment_checkouts (user_id, created_at DESC);

-- Webhook idempotency: aynı sipariş iki kez işlenemez. Kısmi UNIQUE, çünkü
-- ödenmemiş kayıtlarda sipariş kimliği NULL'dur.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_checkouts_order
    ON payment_checkouts (provider, provider_order_id) WHERE provider_order_id IS NOT NULL;

-- Ödemenin sağlayıcıdaki izi faturaya da yazılabilsin diye purchases zaten
-- provider/reference taşıyor (V1); ek kolon gerekmiyor.
