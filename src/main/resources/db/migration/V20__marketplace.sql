-- ============================================================================
-- V20 · PAZAR YERİ (marketplace)
--
-- Model: admin haritada bir POLİGON çizerek pazar yeri açar. Üyeler o pazar
-- yerine kendi projesini/ürününü (startup, mağaza, hizmet) başvuru olarak
-- gönderir. Admin onaylayınca başvuru bir "STANT"a dönüşür ve pazar yerinin
-- SINIRLARI İÇİNDE otomatik yerleştirilmiş bir nokta olarak haritada görünür.
--
-- Neden bölge (territory) satın almaya bağlanmadı: katılım engeli yükselirdi
-- ve pazar yeri geçici/kampanya odaklı bir yapıdır. Stant, bölge değildir —
-- alan sahipliği vermez, çakışma kuralı doğurmaz, ücret alınmaz.
-- ============================================================================

CREATE TABLE marketplaces (
    id              UUID PRIMARY KEY,
    slug            VARCHAR(60)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    tagline         VARCHAR(200),
    description     VARCHAR(2000),
    -- Admin'in serbest çizimi (fiyat bölgeleriyle aynı desen).
    boundary        geometry(Polygon, 4326) NOT NULL,
    center          geometry(Point, 4326)   NOT NULL,
    area_km2        NUMERIC(14, 6) NOT NULL,
    -- Görünüm: haritada pazar yerinin rengi ve rozeti.
    accent_color    VARCHAR(9)   NOT NULL DEFAULT '#8e59ff',
    cover_media_id  UUID REFERENCES media_objects (id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    -- Başvuru penceresi (null = sınırsız).
    opens_at        TIMESTAMPTZ,
    closes_at       TIMESTAMPTZ,
    -- Katılım kuralları.
    max_listings    INT,
    auto_approve    BOOLEAN      NOT NULL DEFAULT FALSE,
    listing_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_marketplaces_slug UNIQUE (slug),
    CONSTRAINT ck_marketplaces_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT ck_marketplaces_window CHECK (closes_at IS NULL OR opens_at IS NULL OR closes_at > opens_at),
    CONSTRAINT ck_marketplaces_max CHECK (max_listings IS NULL OR max_listings > 0)
);
CREATE INDEX idx_marketplaces_boundary ON marketplaces USING GIST (boundary);
CREATE INDEX idx_marketplaces_status   ON marketplaces (status);

-- ---------------------------------------------------------------------------
-- Stantlar / başvurular
-- ---------------------------------------------------------------------------
CREATE TABLE marketplace_listings (
    id               UUID PRIMARY KEY,
    marketplace_id   UUID         NOT NULL REFERENCES marketplaces (id) ON DELETE CASCADE,
    owner_id         UUID         NOT NULL REFERENCES users (id),

    -- ---- başvuru içeriği
    title            VARCHAR(90)  NOT NULL,
    tagline          VARCHAR(140),
    description      VARCHAR(3000) NOT NULL,
    category         VARCHAR(30)  NOT NULL,
    stage            VARCHAR(30),
    website          VARCHAR(300),
    contact_email    VARCHAR(255),
    logo_media_id    UUID REFERENCES media_objects (id),
    cover_media_id   UUID REFERENCES media_objects (id),
    founded_year     INT,
    team_size        INT,
    looking_for      VARCHAR(300),

    -- ---- yerleşim: onaylanınca pazar yerinin İÇİNDE bir nokta atanır
    spot             geometry(Point, 4326),
    spot_index       INT,

    -- ---- iş akışı
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    submitted_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ,
    reviewed_by      UUID REFERENCES users (id),
    review_note      VARCHAR(500),
    featured         BOOLEAN      NOT NULL DEFAULT FALSE,

    view_count       INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_ml_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_ml_category CHECK (category IN
        ('STARTUP', 'ECOMMERCE', 'FOOD', 'ART', 'SERVICE', 'TECH', 'EDUCATION', 'HEALTH', 'TRAVEL', 'OTHER')),
    CONSTRAINT ck_ml_stage CHECK (stage IS NULL OR stage IN
        ('IDEA', 'MVP', 'EARLY_REVENUE', 'GROWTH', 'ESTABLISHED')),
    CONSTRAINT ck_ml_founded CHECK (founded_year IS NULL OR founded_year BETWEEN 1800 AND 2200),
    CONSTRAINT ck_ml_team CHECK (team_size IS NULL OR team_size BETWEEN 1 AND 100000)
);

-- ⚠️ Bir kullanıcı aynı pazar yerine yalnız BİR aktif başvuru yapabilir.
-- Kısmi indeks: reddedilen/geri çekilen başvurudan sonra yeniden başvurabilir.
CREATE UNIQUE INDEX uq_ml_owner_active ON marketplace_listings (marketplace_id, owner_id)
    WHERE status IN ('DRAFT', 'PENDING', 'APPROVED');

CREATE INDEX idx_ml_marketplace_status ON marketplace_listings (marketplace_id, status);
CREATE INDEX idx_ml_owner              ON marketplace_listings (owner_id);
CREATE INDEX idx_ml_pending            ON marketplace_listings (submitted_at) WHERE status = 'PENDING';
CREATE INDEX idx_ml_spot               ON marketplace_listings USING GIST (spot);

-- ---------------------------------------------------------------------------
-- Beğeni: pazar yeri kartlarında sosyal sinyal
-- ---------------------------------------------------------------------------
CREATE TABLE marketplace_listing_likes (
    listing_id UUID        NOT NULL REFERENCES marketplace_listings (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (listing_id, user_id)
);

ALTER TABLE marketplace_listings ADD COLUMN like_count INT NOT NULL DEFAULT 0;
