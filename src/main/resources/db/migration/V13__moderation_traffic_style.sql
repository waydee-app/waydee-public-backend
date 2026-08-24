-- V13: kullanıcı raporlama + kısıtlamalar, trafik (giriş) kayıtları,
--      mesaj istekleri ve bölge görsel özelleştirmesi.

-- ---------------------------------------------------------------- moderasyon
-- Kullanıcı şikayetleri. Kanıt (ekran görüntüsü) media_objects'e yüklenir.
CREATE TABLE user_reports (
    id               UUID PRIMARY KEY,
    reporter_id      UUID         NOT NULL REFERENCES users (id),
    reported_user_id UUID         NOT NULL REFERENCES users (id),
    reason           VARCHAR(40)  NOT NULL,
    description      VARCHAR(1000),
    evidence_media_id UUID        REFERENCES media_objects (id) ON DELETE SET NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    resolution_note  VARCHAR(500),
    handled_by       UUID         REFERENCES users (id),
    handled_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_reports_status CHECK (status IN ('OPEN', 'REVIEWING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT ck_user_reports_self CHECK (reporter_id <> reported_user_id)
);
CREATE INDEX idx_user_reports_status ON user_reports (status, created_at DESC);
CREATE INDEX idx_user_reports_reported ON user_reports (reported_user_id);
-- Aynı kullanıcıyı açık bir şikayetle tekrar tekrar boğmayı engeller.
CREATE UNIQUE INDEX uq_user_reports_open ON user_reports (reporter_id, reported_user_id)
    WHERE status IN ('OPEN', 'REVIEWING');

-- Kullanıcı bazlı eylem kısıtlamaları (mesaj atma, alan alma, paylaşım...).
CREATE TABLE user_restrictions (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    action     VARCHAR(30) NOT NULL,
    reason     VARCHAR(300),
    created_by UUID        REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    CONSTRAINT uq_user_restrictions UNIQUE (user_id, action)
);
CREATE INDEX idx_user_restrictions_user ON user_restrictions (user_id);

-- ---------------------------------------------------------------- trafik
-- Giriş (oturum) olayları: nereden, hangi cihazla, başarılı mı.
CREATE TABLE login_events (
    id           UUID PRIMARY KEY,
    user_id      UUID        REFERENCES users (id) ON DELETE SET NULL,
    username     VARCHAR(60) NOT NULL,
    ip           VARCHAR(45),
    country      VARCHAR(60),
    device       VARCHAR(20),
    browser      VARCHAR(40),
    os           VARCHAR(40),
    surface      VARCHAR(10)  NOT NULL DEFAULT 'app',
    success      BOOLEAN      NOT NULL DEFAULT true,
    user_agent   VARCHAR(400),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_events_created ON login_events (created_at DESC);
CREATE INDEX idx_login_events_user ON login_events (user_id, created_at DESC);
CREATE INDEX idx_login_events_country ON login_events (country);

-- ---------------------------------------------------------------- mesaj istekleri
-- İlk mesaj karşı tarafta "istek" olarak bekler; kabul edilene dek gelen kutusuna düşmez.
ALTER TABLE conversations ADD COLUMN requested_by_id UUID REFERENCES users (id);
ALTER TABLE conversations ADD COLUMN accepted BOOLEAN NOT NULL DEFAULT true;

-- ---------------------------------------------------------------- bölge görünümü
-- Satın alınan dairenin görsel özelleştirmesi + özel efekt.
ALTER TABLE territories ADD COLUMN stroke_color VARCHAR(9);
ALTER TABLE territories ADD COLUMN fill_color   VARCHAR(9);
ALTER TABLE territories ADD COLUMN fill_opacity NUMERIC(3,2);
ALTER TABLE territories ADD COLUMN stroke_width NUMERIC(3,1);
ALTER TABLE territories ADD COLUMN effect       VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE territories ADD CONSTRAINT ck_territories_effect
    CHECK (effect IN ('NONE', 'FIRE', 'PULSE'));
ALTER TABLE territories ADD CONSTRAINT ck_territories_fill_opacity
    CHECK (fill_opacity IS NULL OR (fill_opacity >= 0 AND fill_opacity <= 1));
