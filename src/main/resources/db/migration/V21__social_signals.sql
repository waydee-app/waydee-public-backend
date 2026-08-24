-- ============================================================================
-- V21 · Sosyal sinyaller: daire beğenisi, kaydetme, trend skoru
--
-- Eksik olan neydi: bir daire (bölge) sosyal bir yüzey olmasına rağmen hiçbir
-- doğrudan etkileşim taşımıyordu — beğenilemiyor, kaydedilemiyor, paylaşımı
-- ölçülemiyordu. Gönderi beğenisi vardı ama DAİRENİN kendisi ölçülemiyordu.
-- Sonuç: "yükselişte olan daire" gibi bir sıralama üretmek imkânsızdı.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) Daire beğenisi
-- ---------------------------------------------------------------------------
CREATE TABLE territory_likes (
    territory_id UUID        NOT NULL REFERENCES territories (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (territory_id, user_id)
);
-- "son 7 günde kaç beğeni" sorgusu trend skorunun bileşeni.
CREATE INDEX idx_territory_likes_recent ON territory_likes (territory_id, created_at DESC);
CREATE INDEX idx_territory_likes_user   ON territory_likes (user_id, created_at DESC);

ALTER TABLE territories ADD COLUMN like_count INT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- 2) Kaydetme (yer imi) — kullanıcı beğenmeden de takip etmek isteyebilir
-- ---------------------------------------------------------------------------
CREATE TABLE territory_saves (
    territory_id UUID        NOT NULL REFERENCES territories (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (territory_id, user_id)
);
CREATE INDEX idx_territory_saves_user ON territory_saves (user_id, created_at DESC);

ALTER TABLE territories ADD COLUMN save_count INT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- 3) Trend skoru — periyodik hesaplanır, okuma ucu hazır tabloyu sıralar
--
-- Neden ayrı tablo: skor birden çok sinyalin zaman pencereli toplamıdır
-- (görüntülenme + beğeni + kaydetme + gönderi + yeni takipçi). Her istekte
-- hesaplamak beş ayrı GROUP BY demekti; duyuru şeridi haritanın üstünde
-- sürekli açık duracağı için bu maliyet kabul edilemezdi.
-- ---------------------------------------------------------------------------
CREATE TABLE trending_entries (
    id             UUID PRIMARY KEY,
    -- TERRITORY | USER
    subject_type   VARCHAR(20)    NOT NULL,
    subject_id     UUID           NOT NULL,
    score          NUMERIC(12, 4) NOT NULL,
    -- Önceki turdaki sıra — "yükselişte" oku bununla çizilir (null = yeni giren).
    previous_rank  INT,
    rank_position  INT            NOT NULL,
    -- Skorun neden yüksek olduğunu kullanıcıya söyleyebilmek için bileşenler.
    views_7d       INT            NOT NULL DEFAULT 0,
    likes_7d       INT            NOT NULL DEFAULT 0,
    saves_7d       INT            NOT NULL DEFAULT 0,
    posts_7d       INT            NOT NULL DEFAULT 0,
    followers_7d   INT            NOT NULL DEFAULT 0,
    computed_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_trending_subject UNIQUE (subject_type, subject_id),
    CONSTRAINT ck_trending_type CHECK (subject_type IN ('TERRITORY', 'USER'))
);
CREATE INDEX idx_trending_rank ON trending_entries (subject_type, rank_position);
