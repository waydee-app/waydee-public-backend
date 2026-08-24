-- V29 · Fotoğraf üzerinde ÜRÜN ETİKETLERİ + koleksiyonlar + profil linkleri
--
-- Ürün "içerik" değil, fotoğrafın ÜSTÜNDEKİ BİR NOKTADIR: kullanıcı görsele
-- tıklar, oraya bir etiket bırakır, etiket bir ürüne gider. Bu yüzden konum
-- (x, y) etiketin kendi satırında durur.

CREATE TABLE post_tags (
    id            UUID PRIMARY KEY,
    post_id       UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- ⚠️ KONUM YÜZDE OLARAK saklanır (0–1), piksel DEĞİL. Aynı fotoğraf
    -- telefonda 340px, masaüstünde 620px genişlikte çizilir; piksel saklansaydı
    -- etiket her ekranda başka bir yere düşerdi. Yüzde, görselin kendi
    -- koordinat uzayıdır ve ölçekten bağımsızdır.
    x             NUMERIC(6, 5) NOT NULL,
    y             NUMERIC(6, 5) NOT NULL,
    product_url   VARCHAR(500) NOT NULL,
    product_name  VARCHAR(140),
    -- Fiyat opsiyoneldir: "Auto Fetch Data" açıkken hedef siteden çekilmesi
    -- beklenir; çekilemezse boş kalır ve etikette yalnız ad görünür.
    price         NUMERIC(12, 2),
    currency      VARCHAR(3),
    -- Etiketin küçük görseli (ürün fotoğrafı). Kullanıcının kendi medyası.
    image_media_id UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    -- Aynı fotoğrafta birden çok etiket; sıra kullanıcının ekleme sırasıdır.
    position      INT          NOT NULL DEFAULT 0,
    click_count   INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT       NOT NULL DEFAULT 0,

    -- Konum görselin İÇİNDE olmalı; dışarıdaki bir etiket hiçbir zaman çizilemez.
    CONSTRAINT ck_post_tags_x CHECK (x >= 0 AND x <= 1),
    CONSTRAINT ck_post_tags_y CHECK (y >= 0 AND y <= 1)
);
CREATE INDEX idx_post_tags_post ON post_tags (post_id, position);

COMMENT ON COLUMN post_tags.x IS 'Görselin sol kenarından yatay oran (0–1) — piksel değil';
COMMENT ON COLUMN post_tags.y IS 'Görselin üst kenarından dikey oran (0–1) — piksel değil';

-- ---------------------------------------------------------------- koleksiyon
CREATE TABLE collections (
    id          UUID PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    cover_media_id UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    position    INT          NOT NULL DEFAULT 0,
    item_count  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_collections_owner ON collections (owner_id, position);

-- Bir gönderi birden çok koleksiyonda olabilir; bileşik anahtar yeter.
CREATE TABLE collection_posts (
    collection_id UUID        NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    post_id       UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    position      INT         NOT NULL DEFAULT 0,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (collection_id, post_id)
);
CREATE INDEX idx_collection_posts_post ON collection_posts (post_id);

-- --------------------------------------------------------------- profil linki
CREATE TABLE profile_links (
    id          UUID PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL,
    url         VARCHAR(500) NOT NULL,
    icon_media_id UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    position    INT          NOT NULL DEFAULT 0,
    click_count INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_profile_links_owner ON profile_links (owner_id, position);

-- ------------------------------------------------------------------ sayaçlar
-- Analytics ekranı "Total Saves" ve paylaşım/görüntülenme sayıları istiyor.
-- Gönderi kaydetme (bookmark) henüz yoktu; daire kaydetmeden ayrıdır.
CREATE TABLE post_saves (
    post_id  UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX idx_post_saves_user ON post_saves (user_id, saved_at DESC);

ALTER TABLE posts ADD COLUMN save_count INT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN tag_count  INT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN title      VARCHAR(140);
