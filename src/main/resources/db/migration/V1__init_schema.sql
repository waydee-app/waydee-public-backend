-- WAYDEE V1 — çekirdek şema
-- PostGIS uzantısı postgis/postgis imajında hazır gelir; garanti altına al.
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================ IDENTITY

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(30)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    display_name    VARCHAR(60)  NOT NULL,
    bio             VARCHAR(280),
    avatar_media_id UUID,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT ck_users_role     CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status   CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    ip          VARCHAR(45),
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- ============================================================ GEO

CREATE TABLE countries (
    id                    UUID PRIMARY KEY,
    code                  VARCHAR(2)              NOT NULL,
    name                  VARCHAR(100)            NOT NULL,
    center                geometry(Point, 4326)   NOT NULL,
    boundary              geometry(Polygon, 4326) NOT NULL,
    radius_km             NUMERIC(8, 2)           NOT NULL,
    default_price_per_km2 NUMERIC(12, 2)          NOT NULL,
    currency              VARCHAR(3)              NOT NULL DEFAULT 'USD',
    active                BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ             NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT                  NOT NULL DEFAULT 0,
    CONSTRAINT uq_countries_code UNIQUE (code),
    CONSTRAINT ck_countries_price CHECK (default_price_per_km2 >= 0)
);
CREATE INDEX idx_countries_boundary ON countries USING GIST (boundary);

CREATE TABLE provinces (
    id            UUID PRIMARY KEY,
    country_id    UUID                    NOT NULL REFERENCES countries (id) ON DELETE CASCADE,
    name          VARCHAR(100)            NOT NULL,
    center        geometry(Point, 4326)   NOT NULL,
    boundary      geometry(Polygon, 4326) NOT NULL,
    radius_km     NUMERIC(8, 2)           NOT NULL,
    price_per_km2 NUMERIC(12, 2),
    active        BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ             NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT                  NOT NULL DEFAULT 0,
    CONSTRAINT uq_provinces_country_name UNIQUE (country_id, name),
    CONSTRAINT ck_provinces_price CHECK (price_per_km2 IS NULL OR price_per_km2 >= 0)
);
CREATE INDEX idx_provinces_country  ON provinces (country_id);
CREATE INDEX idx_provinces_boundary ON provinces USING GIST (boundary);

CREATE TABLE districts (
    id            UUID PRIMARY KEY,
    province_id   UUID                    NOT NULL REFERENCES provinces (id) ON DELETE CASCADE,
    name          VARCHAR(100)            NOT NULL,
    center        geometry(Point, 4326)   NOT NULL,
    boundary      geometry(Polygon, 4326) NOT NULL,
    radius_km     NUMERIC(8, 2)           NOT NULL,
    price_per_km2 NUMERIC(12, 2),
    active        BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ             NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT                  NOT NULL DEFAULT 0,
    CONSTRAINT uq_districts_province_name UNIQUE (province_id, name),
    CONSTRAINT ck_districts_price CHECK (price_per_km2 IS NULL OR price_per_km2 >= 0)
);
CREATE INDEX idx_districts_province ON districts (province_id);
CREATE INDEX idx_districts_boundary ON districts USING GIST (boundary);

-- ============================================================ MEDIA

CREATE TABLE media_objects (
    id           UUID PRIMARY KEY,
    owner_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    object_key   VARCHAR(120) NOT NULL,
    content_type VARCHAR(60)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_media_objects_key UNIQUE (object_key)
);
CREATE INDEX idx_media_objects_owner ON media_objects (owner_id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_media
        FOREIGN KEY (avatar_media_id) REFERENCES media_objects (id) ON DELETE SET NULL;

-- ============================================================ TERRITORY

CREATE TABLE territories (
    id           UUID PRIMARY KEY,
    owner_id     UUID                    NOT NULL REFERENCES users (id),
    name         VARCHAR(60)             NOT NULL,
    center       geometry(Point, 4326)   NOT NULL,
    radius_m     INTEGER                 NOT NULL,
    boundary     geometry(Polygon, 4326) NOT NULL,
    area_km2     NUMERIC(12, 4)          NOT NULL,
    price_paid   NUMERIC(12, 2)          NOT NULL,
    currency     VARCHAR(3)              NOT NULL,
    country_id   UUID REFERENCES countries (id),
    province_id  UUID REFERENCES provinces (id),
    district_id  UUID REFERENCES districts (id),
    status       VARCHAR(20)             NOT NULL,
    purchased_at TIMESTAMPTZ             NOT NULL,
    created_at   TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ             NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT                  NOT NULL DEFAULT 0,
    CONSTRAINT ck_territories_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_territories_radius CHECK (radius_m > 0)
);
CREATE INDEX idx_territories_owner    ON territories (owner_id);
CREATE INDEX idx_territories_boundary ON territories USING GIST (boundary);
CREATE INDEX idx_territories_status   ON territories (status);

CREATE TABLE purchases (
    id                UUID PRIMARY KEY,
    territory_id      UUID           NOT NULL REFERENCES territories (id),
    buyer_id          UUID           NOT NULL REFERENCES users (id),
    amount            NUMERIC(12, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    payment_method    VARCHAR(20)    NOT NULL,
    payment_reference VARCHAR(64)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    idempotency_key   VARCHAR(64),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_purchases_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_purchases_status CHECK (status IN ('COMPLETED', 'FAILED'))
);
CREATE INDEX idx_purchases_buyer   ON purchases (buyer_id);
CREATE INDEX idx_purchases_created ON purchases (created_at DESC);

-- ============================================================ SOCIAL

CREATE TABLE territory_profiles (
    territory_id    UUID PRIMARY KEY REFERENCES territories (id) ON DELETE CASCADE,
    title           VARCHAR(80),
    description     VARCHAR(500),
    website         VARCHAR(200),
    cover_media_id  UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    avatar_media_id UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    post_count      INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE posts (
    id            UUID PRIMARY KEY,
    territory_id  UUID          NOT NULL REFERENCES territories (id) ON DELETE CASCADE,
    author_id     UUID          NOT NULL REFERENCES users (id),
    caption       VARCHAR(1000),
    like_count    INTEGER       NOT NULL DEFAULT 0,
    comment_count INTEGER       NOT NULL DEFAULT 0,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX idx_posts_territory_created ON posts (territory_id, created_at DESC);

CREATE TABLE post_media (
    id         UUID PRIMARY KEY,
    post_id    UUID    NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    media_id   UUID    NOT NULL REFERENCES media_objects (id),
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_post_media_order UNIQUE (post_id, sort_order)
);
CREATE INDEX idx_post_media_post ON post_media (post_id);

CREATE TABLE post_likes (
    post_id    UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_likes_user ON post_likes (user_id);

CREATE TABLE post_comments (
    id         UUID PRIMARY KEY,
    post_id    UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id  UUID         NOT NULL REFERENCES users (id),
    body       VARCHAR(500) NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_post_comments_post ON post_comments (post_id, created_at);

-- ============================================================ PLATFORM

CREATE TABLE audit_logs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id       UUID,
    actor_username VARCHAR(30),
    action         VARCHAR(60) NOT NULL,
    entity_type    VARCHAR(40),
    entity_id      VARCHAR(64),
    detail         JSONB,
    ip             VARCHAR(45),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_action  ON audit_logs (action);

CREATE TABLE app_settings (
    setting_key VARCHAR(60) PRIMARY KEY,
    value       TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
