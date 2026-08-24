-- V16 · Sosyal medya bağlantıları · bölgeye hikaye · faturalandırma — 28 Temmuz 2026
--
-- Üç bağımsız yetenek tek migration'da toplanır:
--   1) user_social_links      → kullanıcının sosyal medya hesapları (ikonlu gösterim)
--   2) stories.territory_id   → hikaye bir bölge (harita profili) üzerinde yayınlanabilir
--   3) invoices               → her satın almanın faturası (mevcutlar geriye dönük kesilir)

-- ─────────────────────────────────────────────────────────── 1) sosyal medya
-- Ayrı tablo (users'a 10 kolon eklemek yerine): yeni platform eklemek migration
-- gerektirmez, sıralama kullanıcıya bırakılabilir, boş değer satır tutmaz.
CREATE TABLE IF NOT EXISTS user_social_links (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    platform    VARCHAR(20)  NOT NULL,
    value       VARCHAR(200) NOT NULL,
    position    INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_social_links UNIQUE (user_id, platform),
    CONSTRAINT ck_user_social_platform CHECK (platform IN (
        'WEBSITE', 'INSTAGRAM', 'FACEBOOK', 'X', 'YOUTUBE',
        'TIKTOK', 'SNAPCHAT', 'LINKEDIN', 'TELEGRAM', 'GITHUB'))
);

CREATE INDEX IF NOT EXISTS idx_user_social_links_user ON user_social_links (user_id, position);

-- Bölge profilinde sosyal bağlantılar gösterilsin mi (göster/gizle anahtarı).
ALTER TABLE territory_profiles
    ADD COLUMN IF NOT EXISTS show_social_links BOOLEAN NOT NULL DEFAULT TRUE;

-- ─────────────────────────────────────────────────────── 2) bölgede hikaye
-- NULL = yalnız kullanıcı profilinde görünen klasik hikaye.
-- Bölge silinirse hikaye kalır ama bölge bağı düşer (SET NULL).
ALTER TABLE stories
    ADD COLUMN IF NOT EXISTS territory_id UUID REFERENCES territories (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_stories_territory ON stories (territory_id, expires_at DESC);

-- ────────────────────────────────────────────────────────── 3) faturalar
-- Fatura, satın alma anında kesilir ve DEĞİŞMEZ: alıcı adı/e-postası, bölge
-- etiketi ve birim fiyat o günkü hâliyle saklanır (sonradan değişirse fatura
-- bozulmasın). Gerçek ödeme altyapısı geldiğinde `status` ve ödeme alanları
-- kullanılmaya devam eder.
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS invoices (
    id                UUID PRIMARY KEY,
    invoice_no        VARCHAR(24)    NOT NULL,
    user_id           UUID           NOT NULL REFERENCES users (id),
    territory_id      UUID           REFERENCES territories (id) ON DELETE SET NULL,
    purchase_id       UUID,
    issued_at         TIMESTAMPTZ    NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'PAID',

    -- alıcı anlık bilgileri (fatura kesildiği andaki hâli)
    buyer_username    VARCHAR(30)    NOT NULL,
    buyer_name        VARCHAR(60)    NOT NULL,
    buyer_email       VARCHAR(255)   NOT NULL,

    -- kalem detayı
    description       VARCHAR(160)   NOT NULL,
    region_label      VARCHAR(200),
    area_km2          NUMERIC(14, 6) NOT NULL,
    radius_m          INT            NOT NULL,
    price_per_km2     NUMERIC(12, 2),

    -- tutarlar
    currency          VARCHAR(3)     NOT NULL,
    subtotal          NUMERIC(12, 2) NOT NULL,
    tax_rate          NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total             NUMERIC(12, 2) NOT NULL,

    -- ödeme izi
    payment_method    VARCHAR(20),
    payment_reference VARCHAR(64),

    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoices_no UNIQUE (invoice_no),
    CONSTRAINT uq_invoices_purchase UNIQUE (purchase_id),
    CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_invoices_user ON invoices (user_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_invoices_issued ON invoices (issued_at DESC);

-- Geriye dönük faturalandırma: bugüne kadarki her satın alma için fatura üret.
-- Fatura no: WD-<yıl>-<6 hane sıra>. Sıra numarası satın alma tarihine göre verilir.
INSERT INTO invoices (id, invoice_no, user_id, territory_id, purchase_id, issued_at, status,
                      buyer_username, buyer_name, buyer_email,
                      description, region_label, area_km2, radius_m, price_per_km2,
                      currency, subtotal, tax_rate, tax_amount, total,
                      payment_method, payment_reference, created_at)
SELECT gen_random_uuid(),
       'WD-' || to_char(p.created_at, 'YYYY') || '-' ||
           lpad(nextval('invoice_number_seq')::TEXT, 6, '0'),
       p.buyer_id,
       p.territory_id,
       p.id,
       p.created_at,
       CASE WHEN p.status = 'COMPLETED' THEN 'PAID' ELSE 'ISSUED' END,
       u.username,
       u.display_name,
       u.email,
       'Bölge satın alımı — ' || COALESCE(t.name, 'Bölge'),
       NULL,
       COALESCE(t.area_km2, 0),
       COALESCE(t.radius_m, 0),
       CASE WHEN COALESCE(t.area_km2, 0) > 0 THEN round(p.amount / t.area_km2, 2) END,
       p.currency,
       p.amount,
       0,
       0,
       p.amount,
       p.payment_method,
       p.payment_reference,
       p.created_at
FROM purchases p
         JOIN users u ON u.id = p.buyer_id
         LEFT JOIN territories t ON t.id = p.territory_id
WHERE NOT EXISTS (SELECT 1 FROM invoices i WHERE i.purchase_id = p.id)
ORDER BY p.created_at;
