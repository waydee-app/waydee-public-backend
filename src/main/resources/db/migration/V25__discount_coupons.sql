-- V25 · İndirim kuponları — 2 Ağustos 2026
--
-- Yönetici kupon tanımlar (yüzde ya da sabit tutar), üye bölge kiralarken
-- kodu girer, indirim ödeme oturumu açılırken uygulanır.
--
-- ⚠️ EN KRİTİK TASARIM KARARI — KUPON NE ZAMAN "KULLANILMIŞ" SAYILIR?
-- Ödeme asenkron: oturum açılır, kullanıcı sağlayıcıya gider, sonuç webhook ile
-- döner. Kuponu oturum açılırken *kesin* tüketirsek, ödemeden vazgeçen her
-- kullanıcı kuponu yakar (100 kullanımlık kupon 100 vazgeçmeyle biter).
-- Hiç tüketmezsek, 1 kullanımlık kupon aynı anda 50 kişi tarafından kullanılır.
--
-- Çözüm: kullanım kaydı ÜÇ durumlu.
--   RESERVED  → oturum açıldı, kontenjandan düşüldü, ödeme bekleniyor
--   CONFIRMED → ödeme geldi, kullanım kesinleşti
--   RELEASED  → oturum süresi doldu/iptal → kontenjan GERİ VERİLİR
-- Sayaç `redemption_count` yalnız RESERVED+CONFIRMED'ı sayar.

CREATE TABLE IF NOT EXISTS discount_coupons (
    id                UUID PRIMARY KEY,
    -- Her zaman BÜYÜK harfe normalize edilir; kullanıcı küçük yazsa da tutar.
    code              VARCHAR(40)    NOT NULL,
    description       VARCHAR(200),

    -- PERCENT → percent_off kullanılır · FIXED → amount_off kullanılır
    discount_type     VARCHAR(10)    NOT NULL,
    percent_off       NUMERIC(5, 2),
    amount_off        NUMERIC(14, 2),
    -- Sabit tutarlı kuponda hangi para birimi olduğu belirsiz kalmasın.
    currency          VARCHAR(3),

    -- Alt sınır: "500 TL üzeri alışverişte geçerli"
    min_amount        NUMERIC(14, 2),
    -- Yüzde kuponlarında tavan: "%50 indirim, en fazla 200 TL"
    max_discount      NUMERIC(14, 2),

    -- PURCHASE | RENEWAL | BOTH
    applies_to        VARCHAR(10)    NOT NULL DEFAULT 'BOTH',

    -- NULL = sınırsız
    max_redemptions   INTEGER,
    max_per_user      INTEGER,
    -- RESERVED + CONFIRMED sayısı. Atomik UPDATE ile artar/azalır.
    redemption_count  INTEGER        NOT NULL DEFAULT 0,

    starts_at         TIMESTAMPTZ,
    ends_at           TIMESTAMPTZ,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,

    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_discount_coupons_code UNIQUE (code),
    CONSTRAINT ck_discount_coupons_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT ck_discount_coupons_applies CHECK (applies_to IN ('PURCHASE', 'RENEWAL', 'BOTH')),
    -- Tipine göre ilgili alan dolu olmalı; yoksa "indirimsiz indirim" kuponu doğar.
    CONSTRAINT ck_discount_coupons_value CHECK (
        (discount_type = 'PERCENT' AND percent_off IS NOT NULL AND percent_off > 0 AND percent_off <= 100)
        OR (discount_type = 'FIXED' AND amount_off IS NOT NULL AND amount_off > 0)
    ),
    CONSTRAINT ck_discount_coupons_window CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_discount_coupons_active
    ON discount_coupons (active, ends_at) WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id               UUID PRIMARY KEY,
    coupon_id        UUID           NOT NULL REFERENCES discount_coupons (id) ON DELETE CASCADE,
    user_id          UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Hangi ödeme oturumuna bağlı (serbest bırakma bunun üzerinden yapılır).
    checkout_id      UUID,
    territory_id     UUID,

    -- Raporlama tutarları o günkü hâliyle KOPYALANIR; kupon sonradan
    -- değiştirilse bile geçmiş rapor bozulmaz (fatura mantığının aynısı).
    original_amount  NUMERIC(14, 2) NOT NULL,
    discount_amount  NUMERIC(14, 2) NOT NULL,
    final_amount     NUMERIC(14, 2) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    coupon_code      VARCHAR(40)    NOT NULL,

    status           VARCHAR(10)    NOT NULL,
    confirmed_at     TIMESTAMPTZ,
    released_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_coupon_redemptions_status CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED'))
);

-- "Bu kullanıcı bu kuponu kaç kez kullandı" (max_per_user kontrolü) sıcak yolda.
CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_user
    ON coupon_redemptions (coupon_id, user_id) WHERE status <> 'RELEASED';
-- Rapor: kupon bazında kırılım.
CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_coupon
    ON coupon_redemptions (coupon_id, created_at DESC);
-- Ödeme oturumu kapanınca kaydı bulmak için.
CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_redemptions_checkout
    ON coupon_redemptions (checkout_id) WHERE checkout_id IS NOT NULL;

-- Ödeme oturumu hangi kuponu taşıyor + indirim öncesi/sonrası tutar.
ALTER TABLE payment_checkouts ADD COLUMN IF NOT EXISTS coupon_id       UUID;
ALTER TABLE payment_checkouts ADD COLUMN IF NOT EXISTS coupon_code     VARCHAR(40);
ALTER TABLE payment_checkouts ADD COLUMN IF NOT EXISTS original_amount NUMERIC(14, 2);
ALTER TABLE payment_checkouts ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14, 2);

-- Fatura indirimi göstermek ZORUNDA: `amount` tahsil edilen tutardır, ama
-- muhasebe "ne kadar indirim verildi"i de bilmelidir.
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS coupon_code     VARCHAR(40);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14, 2);
