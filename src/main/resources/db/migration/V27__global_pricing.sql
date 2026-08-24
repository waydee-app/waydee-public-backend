-- V27 — KÜRESEL FİYAT KATMANI (3 Ağustos 2026)
--
-- Tüm dünyada geçerli, haritada ÇİZİLMEYEN bir taban fiyat.
--
-- ⚠️ Neden gerekli: fiyat şimdiye kadar yalnız admin'in çizdiği poligonlardan
-- ve idari hiyerarşiden (ilçe→il→ülke) çözülüyordu. Hiçbiri yoksa `resolve()`
-- boş dönüyor ve kullanıcı "REGION_NOT_AVAILABLE" görüyordu — yani haritanın
-- büyük bölümünde hiçbir şey satılamıyordu. Bu katman o boşluğu kapatır:
-- tanımlıysa dünyanın HER noktasında bir fiyat vardır.
--
-- ⚠️ Katmanın geometrisi YOKTUR (bu yüzden GIST indeks de yok): "her yer"
-- demek, bir poligonla ifade edilemeyecek kadar geniş — ayrıca haritada
-- çizilmemesi istendi.
--
-- ⚠️ TEK SATIR: `id` sabit 1 ve CHECK ile zorlanır. Birden çok küresel taban
-- "hangisi geçerli?" sorusunu doğurur; tek satır bu soruyu ortadan kaldırır.
CREATE TABLE global_pricing
(
    id           SMALLINT       NOT NULL PRIMARY KEY DEFAULT 1,
    -- Birim başına fiyat. Birimi `unit` belirler (M2 → metrekare, KM2 → km²).
    price        NUMERIC(18, 6) NOT NULL,
    -- ⚠️ Birim ayrı tutulur: yönetici "metrekare 0,05 TL" demek isteyebilir,
    -- km² üzerinden 50.000 TL yazmak zorunda kalmamalı. Çözümlemede km²'ye
    -- normalize edilir (M2 × 1.000.000).
    unit         VARCHAR(8)     NOT NULL DEFAULT 'M2',
    currency     VARCHAR(3)     NOT NULL DEFAULT 'TRY',
    -- Kapalıyken katman yokmuş gibi davranılır (eski hiyerarşi tek başına kalır).
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_global_pricing_single CHECK (id = 1),
    CONSTRAINT ck_global_pricing_unit CHECK (unit IN ('M2', 'KM2')),
    CONSTRAINT ck_global_pricing_price CHECK (price >= 0)
);

-- Başlangıçta KAPALI bir satır: yönetici panelden fiyatı girip açana kadar
-- davranış birebir eskisi gibi kalır (sessiz bir fiyat değişikliği olmaz).
INSERT INTO global_pricing (id, price, unit, currency, active)
VALUES (1, 0, 'M2', 'TRY', FALSE);

-- ---------------------------------------------------------------------------
-- SÜRELİ KİRALAMA
--
-- Kira artık yalnız 12 ay değil: 1 · 7 · 14 · 30 · 90 · 180 · 278 · 365 gün.
-- Amaç uzun süreli satışı teşvik etmek — kısa süreler gün başına daha pahalı
-- (çarpanlar `LeaseDuration` içinde, oradaki javadoc türetmeyi anlatır).
--
-- ⚠️ Gün cinsinden ayrı kolon: `lease_months` ile 7 günlük bir kira ifade
-- edilemiyor (0 ay?). Eski kayıtlar için ay × 30 yerine GERÇEK gün farkı
-- yazılır — böylece mevcut bitiş tarihleri değişmez.
ALTER TABLE territories ADD COLUMN lease_days INT;
UPDATE territories
   SET lease_days = GREATEST(1, (DATE_PART('day', expires_at - lease_started_at))::INT)
 WHERE expires_at IS NOT NULL
   AND lease_started_at IS NOT NULL;
UPDATE territories SET lease_days = 365 WHERE lease_days IS NULL;
ALTER TABLE territories ALTER COLUMN lease_days SET NOT NULL;
ALTER TABLE territories ALTER COLUMN lease_days SET DEFAULT 365;

-- Rezervasyon da süreyi taşımalı: ödeme sayfasında geçen sürede seçim
-- kaybolursa kullanıcı 1 gün ödeyip 365 gün alabilirdi.
ALTER TABLE payment_checkouts ADD COLUMN lease_days INT NOT NULL DEFAULT 365;
