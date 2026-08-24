-- V32 · GÖNDERİYE ait sosyal bağlantılar
--
-- ⚠️ `user_social_links` (V16) ile KARIŞTIRILMAMALI: o, kullanıcının kendi
-- hesaplarıdır ve profilinde durur. Buradakiler **tek bir gönderiye** aittir:
-- "bu fotoğraftaki ürünü şu Instagram hesabında bulursun". Aynı kullanıcı
-- farklı gönderilerde farklı hesaplar gösterebilir; tek bir kullanıcı listesi
-- bunu ifade edemezdi.
CREATE TABLE post_social_links (
    id         UUID PRIMARY KEY,
    post_id    UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    -- Referansın platform listesi (Select platform): INSTAGRAM · TIKTOK ·
    -- SNAPCHAT · X · THREADS · MAIL · LINK · WEBSITE.
    platform   VARCHAR(20)  NOT NULL,
    -- Ham girdi: kullanıcı adı ya da tam adres olabilir; tam adres sunucuda
    -- çözülür (`SocialLinkService.resolveUrl` deseni).
    value      VARCHAR(300) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_post_social_platform CHECK (platform IN
        ('INSTAGRAM','TIKTOK','SNAPCHAT','X','THREADS','MAIL','LINK','WEBSITE')),
    -- Bir gönderide aynı platform iki kez görünmez (ikinci ekleme günceller).
    CONSTRAINT uq_post_social UNIQUE (post_id, platform)
);
CREATE INDEX idx_post_social_post ON post_social_links (post_id, position);

COMMENT ON TABLE post_social_links IS
    'Gönderiye ait sosyal hesaplar — kullanıcının profil hesaplarından (user_social_links) ayrıdır.';
