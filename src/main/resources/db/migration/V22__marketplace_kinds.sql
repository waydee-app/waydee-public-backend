-- ============================================================================
-- V22 · Pazar yeri TİPLERİ + yapılandırılabilir başvuru formu
--
-- Sorun: pazar yeri yalnız "startup vitrini" varsayımıyla kurulmuştu. Başvuru
-- formu sabitti (kuruluş yılı, ekip büyüklüğü, aşama…) ve bir "yürüyüş
-- etkinliği" ya da "ikinci el ilan" pazarında bu alanlar anlamsız kalıyor,
-- gereken alanlar (tarih, kontenjan, fiyat, buluşma noktası) ise hiç yoktu.
--
-- ÇÖZÜM — iki katman:
--   1) `kind`: pazarın türü. Türe göre MANTIKLI VARSAYILANLAR gelir.
--   2) `form_schema` (JSONB): adminin o pazar için formu birebir ayarlaması.
--      Hangi hazır alan görünsün/zorunlu olsun + kendi ek soruları.
--
-- Neden tip başına ayrı tablo/kolon patlaması YAPILMADI: her yeni tür yeni
-- migration ve yeni DTO demekti. Yaygın alanlar tek bir opsiyonel kolon
-- kümesinde toplanır (tarih, konum, fiyat, kontenjan); geri kalan her şey
-- adminin tanımladığı serbest sorulardır.
-- ============================================================================

ALTER TABLE marketplaces ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE marketplaces ADD CONSTRAINT ck_marketplaces_kind CHECK (kind IN
    ('GENERAL', 'STARTUP', 'LISTING', 'EVENT', 'TOUR', 'WALK', 'FOOD', 'ART'));

-- Adminin tasarladığı form. NULL → türün varsayılan şeması kullanılır.
ALTER TABLE marketplaces ADD COLUMN form_schema JSONB;

-- Başvuru sahibine gösterilecek serbest metin (kurallar, katılım şartları).
ALTER TABLE marketplaces ADD COLUMN application_note VARCHAR(1000);

-- Mevcut tek pazar startup vitriniydi; tipini koru.
UPDATE marketplaces SET kind = 'STARTUP' WHERE kind = 'GENERAL';

CREATE INDEX idx_marketplaces_kind ON marketplaces (kind);

-- ---------------------------------------------------------------------------
-- Stant: türe göre anlam kazanan ORTAK opsiyonel alanlar
-- ---------------------------------------------------------------------------
-- Etkinlik / gezi / yürüyüş
ALTER TABLE marketplace_listings ADD COLUMN starts_at      TIMESTAMPTZ;
ALTER TABLE marketplace_listings ADD COLUMN ends_at        TIMESTAMPTZ;
ALTER TABLE marketplace_listings ADD COLUMN location_label VARCHAR(200);
ALTER TABLE marketplace_listings ADD COLUMN capacity       INT;
-- İlan / alım-satım
ALTER TABLE marketplace_listings ADD COLUMN price          NUMERIC(12, 2);
ALTER TABLE marketplace_listings ADD COLUMN currency       VARCHAR(3);
ALTER TABLE marketplace_listings ADD COLUMN condition_code VARCHAR(20);
-- İletişim
ALTER TABLE marketplace_listings ADD COLUMN contact_phone  VARCHAR(30);
-- Adminin tanımladığı serbest soruların cevapları: { "soru-anahtari": "cevap" }
ALTER TABLE marketplace_listings ADD COLUMN custom_fields  JSONB;
-- Ek görseller (galeri) — logo/kapak dışında en fazla 8 medya
ALTER TABLE marketplace_listings ADD COLUMN gallery_media_ids UUID[];

ALTER TABLE marketplace_listings ADD CONSTRAINT ck_ml_capacity
    CHECK (capacity IS NULL OR capacity BETWEEN 1 AND 1000000);
ALTER TABLE marketplace_listings ADD CONSTRAINT ck_ml_price
    CHECK (price IS NULL OR price >= 0);
ALTER TABLE marketplace_listings ADD CONSTRAINT ck_ml_window
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at);
ALTER TABLE marketplace_listings ADD CONSTRAINT ck_ml_condition
    CHECK (condition_code IS NULL OR condition_code IN ('NEW', 'LIKE_NEW', 'GOOD', 'USED', 'FOR_PARTS'));

-- Yaklaşan etkinlikleri sıralamak için.
CREATE INDEX idx_ml_starts_at ON marketplace_listings (starts_at)
    WHERE starts_at IS NOT NULL AND status = 'APPROVED';
