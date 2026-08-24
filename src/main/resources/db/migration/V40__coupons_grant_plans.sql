-- V40 · KUPONLAR ARTIK İNDİRİM DEĞİL, PAKET HEDİYESİDİR
--
-- 🔴 Eski model: kupon bir **indirim**ti (yüzde ya da sabit tutar) ve yalnız
-- **bölge ödemelerine** uygulanıyordu. Bölge ödemesi V38'de kaldırıldı
-- (daire artık Premium üyeliğin hakkı), `POST /coupons/preview` de onunla
-- birlikte gitti. Yani indirim kuponlarının uygulanacağı **hiçbir yer
-- kalmamıştı** — tablo çalışır, kod çalışır, ama hiçbir şey yapmazlardı.
--
-- Yeni model: kupon bir **paket hediyesi**dir. Yönetici "PREMIUM · yıllık"
-- içerikli bir kod üretir; kullanıcı kodu girer ve **planı anında** o pakete
-- yükselir. Ödeme akışına hiç uğranmaz.
--
-- ⚠️ Veri SİLİNMEZ. Eski indirim kuponları ve kullanım kayıtları duruyor;
-- yalnız **pasife alınıyorlar** çünkü uygulanacakları bir ödeme yok. Geçmiş
-- kullanım raporu (`coupon_redemptions`) olduğu gibi okunabilir kalır.

-- --------------------------------------------------------------- hediye alanları
ALTER TABLE discount_coupons ADD COLUMN IF NOT EXISTS grant_plan VARCHAR(10);
ALTER TABLE discount_coupons ADD COLUMN IF NOT EXISTS grant_period VARCHAR(10);

ALTER TABLE discount_coupons DROP CONSTRAINT IF EXISTS ck_discount_coupons_grant;
ALTER TABLE discount_coupons ADD CONSTRAINT ck_discount_coupons_grant
    CHECK (grant_plan IS NULL OR grant_plan IN ('PRO', 'PREMIUM'));

ALTER TABLE discount_coupons DROP CONSTRAINT IF EXISTS ck_discount_coupons_grant_period;
ALTER TABLE discount_coupons ADD CONSTRAINT ck_discount_coupons_grant_period
    CHECK (grant_period IS NULL OR grant_period IN ('MONTHLY', 'YEARLY'));

-- 🔴 Yeni tür: `PLAN`. Eski `PERCENT`/`FIXED` değerleri **korunuyor** —
-- geçmiş satırlar okunabilir kalmalı.
ALTER TABLE discount_coupons DROP CONSTRAINT IF EXISTS ck_discount_coupons_type;
ALTER TABLE discount_coupons ADD CONSTRAINT ck_discount_coupons_type
    CHECK (discount_type IN ('PERCENT', 'FIXED', 'PLAN'));

-- 🔴 ESKİ `ck_discount_coupons_value` PLAN TÜRÜNÜ REDDEDİYOR (ölçüldü).
-- Kısıt "ya PERCENT + percent_off dolu, ya FIXED + amount_off dolu" diyordu;
-- üçüncü bir tür eklenince her PLAN satırı bu kısıtta düşüyordu
-- (`violates check constraint "ck_discount_coupons_value"`, 409 CONFLICT).
-- Yeni sürüm PLAN'ı da tanır ve onda tutar alanlarının BOŞ olmasını ister.
ALTER TABLE discount_coupons DROP CONSTRAINT IF EXISTS ck_discount_coupons_value;
ALTER TABLE discount_coupons ADD CONSTRAINT ck_discount_coupons_value
    CHECK (
        (discount_type = 'PERCENT' AND percent_off IS NOT NULL
            AND percent_off > 0 AND percent_off <= 100)
        OR (discount_type = 'FIXED' AND amount_off IS NOT NULL AND amount_off > 0)
        OR (discount_type = 'PLAN' AND percent_off IS NULL AND amount_off IS NULL)
    );

-- Hediye kuponunda yüzde/tutar YOKTUR; indirim kuponunda hediye alanı yoktur.
-- Bu kural veritabanında durur: eksik yazılmış bir kupon "kod geçerli ama
-- hiçbir şey vermiyor" hâline düşmemeli.
ALTER TABLE discount_coupons DROP CONSTRAINT IF EXISTS ck_discount_coupons_shape;
ALTER TABLE discount_coupons ADD CONSTRAINT ck_discount_coupons_shape
    CHECK (
        (discount_type = 'PLAN' AND grant_plan IS NOT NULL AND grant_period IS NOT NULL)
        OR (discount_type <> 'PLAN' AND grant_plan IS NULL)
    );

COMMENT ON COLUMN discount_coupons.grant_plan IS
    'Kodun hediye ettiği paket: PRO | PREMIUM. İndirim kuponlarında NULL.';
COMMENT ON COLUMN discount_coupons.grant_period IS
    'Hediyenin süresi: MONTHLY (30 gün) | YEARLY (365 gün).';

-- ------------------------------------------------- eski indirim kuponları
-- Uygulanacak bir ödeme kalmadığı için pasife alınıyorlar. Silinmiyorlar:
-- kullanım geçmişi bu satırlara bağlı.
UPDATE discount_coupons
SET active = false, updated_at = now()
WHERE discount_type IN ('PERCENT', 'FIXED') AND active = true;

-- --------------------------------------------------------- kullanım kaydı
-- Hediye kullanımında tutar diye bir şey yok; kolonlar NOT NULL olduğu için
-- sıfır yazılacak. Anlamı yorumla sabitleniyor.
COMMENT ON COLUMN coupon_redemptions.original_amount IS
    'İndirim kuponlarında tutar. PLAN kuponlarında 0 — hediye bir ödeme değildir.';

-- 🔴 Aynı kullanıcı aynı hediye kodunu İKİ KEZ kullanamaz. Uygulama katmanı
-- da bakıyor ama iki eşzamanlı istek ikisini birden geçirebilirdi.
-- ⚠️ Kısmi indeks: yalnız onaylanmış kayıtlar sayılır; serbest bırakılmış
-- (RELEASED) bir kayıt kullanıcıyı kilitlememeli.
CREATE UNIQUE INDEX IF NOT EXISTS ux_coupon_redemptions_user_coupon
    ON coupon_redemptions (coupon_id, user_id)
    WHERE status = 'CONFIRMED';
