-- V34 · PRO planına geçiş için ödeme oturumu
--
-- Plan yükseltmesi de bir ödemedir; bu yüzden ayrı bir mekanizma icat etmek
-- yerine mevcut `payment_checkouts` durum makinesi (PENDING → PAID/FAILED,
-- imzalı webhook, idempotent tamamlama) yeniden kullanılır.
--
-- ⚠️ Yeni tür `PLAN_PRO`. Daire alımından farkı: GEOMETRİ YOKTUR — bu yüzden
-- ilgili kolonlar zaten nullable olmalı. Aşağıdaki ALTER'lar, daire için
-- zorunlu tutulmuş kolonları isteğe bağlıya çevirir.
ALTER TABLE payment_checkouts DROP CONSTRAINT IF EXISTS ck_payment_checkouts_kind;
ALTER TABLE payment_checkouts ADD CONSTRAINT ck_payment_checkouts_kind
    CHECK (kind IN ('TERRITORY_PURCHASE', 'TERRITORY_RENEWAL', 'PLAN_PRO'));

ALTER TABLE payment_checkouts ALTER COLUMN center DROP NOT NULL;
ALTER TABLE payment_checkouts ALTER COLUMN boundary DROP NOT NULL;
ALTER TABLE payment_checkouts ALTER COLUMN radius_m DROP NOT NULL;
ALTER TABLE payment_checkouts ALTER COLUMN area_km2 DROP NOT NULL;
ALTER TABLE payment_checkouts ALTER COLUMN price_per_km2 DROP NOT NULL;
ALTER TABLE payment_checkouts ALTER COLUMN territory_name DROP NOT NULL;

COMMENT ON COLUMN payment_checkouts.kind IS
    'TERRITORY_PURCHASE | TERRITORY_RENEWAL | PLAN_PRO (üyelik yükseltmesi, geometrisi yok)';
