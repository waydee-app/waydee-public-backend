-- V37 · PREMIUM planı + YILLIK faturalama
--
-- İki değişiklik birlikte gelir çünkü ikisi de aynı kolonları büyütür:
--
-- 1. **Üçüncü plan: PREMIUM.** Bugüne kadar iki plan vardı (FREE, PRO). Artık
--    haritadaki **mağaza dairesi** PRO'nun değil PREMIUM'un hakkıdır — daire
--    çizmek ayrı bir km² alışverişi olmaktan çıkıp bir **üyelik hakkına**
--    dönüşür (bkz. V38).
--
-- 2. **Yıllık faturalama.** Fiyat tablosu artık dört hücre:
--       PRO      aylık 13 $   ·   yıllık 10 $/ay (12 ay peşin = 120 $)
--       PREMIUM  aylık 30 $   ·   yıllık 25 $/ay (12 ay peşin = 300 $)
--    ⚠️ Yıllık fiyat **aylık eşdeğerdir**, yıllık toplam değil: kullanıcı
--    "10 $/ay, yıllık faturalanır" görür, tahsilat 12 katıdır. Toplamı saklamak
--    ekranda tekrar bölmeyi gerektirirdi ve indirim oranı kaybolurdu.
--
-- ⚠️ Dönem BİLGİSİ neden saklanıyor: süre uzatması aya mı yıla mı yapılacağını
-- (`grantPlan`) ve "yıllık üyesiniz" yazısını çizmek için gerekli. Bitiş
-- tarihinden geriye çıkarmak, kalan süre birikimli uzadığı için yanıltırdı.

-- ------------------------------------------------------------------ users
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_plan;
ALTER TABLE users ADD CONSTRAINT ck_users_plan
    CHECK (plan IN ('FREE', 'PRO', 'PREMIUM'));

-- `plan` kolonu VARCHAR(10) idi; 'PREMIUM' 7 karakter, sığıyor — genişletmeye
-- gerek yok. Yine de ileride daha uzun bir ad gelirse diye kayıt düşülüyor.

ALTER TABLE users ADD COLUMN IF NOT EXISTS plan_period VARCHAR(10);
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_plan_period;
ALTER TABLE users ADD CONSTRAINT ck_users_plan_period
    CHECK (plan_period IS NULL OR plan_period IN ('MONTHLY', 'YEARLY'));

-- Mevcut ücretli üyelerin hepsi aylıktı (yıllık seçenek yeni geliyor).
UPDATE users SET plan_period = 'MONTHLY'
WHERE plan <> 'FREE' AND plan_period IS NULL;

COMMENT ON COLUMN users.plan IS
    'FREE: günde 1 gönderi, 3 etiket. PRO: sınırsız gönderi/etiket + mavi tik. '
    'PREMIUM: PRO''nun hepsi + haritada 100 m mağaza dairesi.';
COMMENT ON COLUMN users.plan_period IS
    'MONTHLY | YEARLY — üyeliğin faturalama dönemi. FREE hesapta NULL.';

-- 🔴 Süpürme indeksi PRO''ya kısıtlıydı; PREMIUM satırları taranmaz olurdu.
DROP INDEX IF EXISTS idx_users_plan_expires;
CREATE INDEX idx_users_plan_expires
    ON users (plan_expires_at)
    WHERE plan <> 'FREE';

-- ------------------------------------------------------- payment_checkouts
ALTER TABLE payment_checkouts DROP CONSTRAINT IF EXISTS ck_payment_checkouts_kind;
ALTER TABLE payment_checkouts ADD CONSTRAINT ck_payment_checkouts_kind
    CHECK (kind IN ('TERRITORY_PURCHASE', 'TERRITORY_RENEWAL', 'PLAN_PRO', 'PLAN_PREMIUM'));

ALTER TABLE payment_checkouts ADD COLUMN IF NOT EXISTS plan_period VARCHAR(10);
ALTER TABLE payment_checkouts DROP CONSTRAINT IF EXISTS ck_payment_checkouts_plan_period;
ALTER TABLE payment_checkouts ADD CONSTRAINT ck_payment_checkouts_plan_period
    CHECK (plan_period IS NULL OR plan_period IN ('MONTHLY', 'YEARLY'));

-- Geçmiş plan ödemelerinin hepsi aylıktı.
UPDATE payment_checkouts SET plan_period = 'MONTHLY'
WHERE kind = 'PLAN_PRO' AND plan_period IS NULL;

COMMENT ON COLUMN payment_checkouts.kind IS
    'TERRITORY_PURCHASE | TERRITORY_RENEWAL | PLAN_PRO | PLAN_PREMIUM '
    '(üyelik ödemelerinin geometrisi yoktur)';
COMMENT ON COLUMN payment_checkouts.plan_period IS
    'Üyelik ödemesinin dönemi: MONTHLY | YEARLY. Bölge ödemelerinde NULL.';

-- ----------------------------------------------------------- app_settings
-- Fiyatlar çalışma zamanında yönetim panelinden değişir (PlanPricingService).
--
-- 🔴 Değerler **AÇIKÇA** yazılır (`DO UPDATE`), eski `plan.pro.price`
-- anahtarından devralınmaz. İlk yazımda devralma vardı ve ölçüldüğünde PRO
-- aylık fiyatı yeni tabloya **10,00** olarak indi: veritabanında duran eski
-- kayıt, bu sürümle birlikte alınan "PRO aylık 13 $" kararını sessizce eziyordu.
-- Yeni fiyat tablosu bir yönetici tercihi değil, ürün kararıdır — migration onu
-- uygular. Yönetici isterse panelden yine değiştirir.
INSERT INTO app_settings (setting_key, value, updated_at)
VALUES ('plan.pro.monthly.price', '13.00', now()),
       ('plan.pro.yearly.price', '10.00', now()),
       ('plan.premium.monthly.price', '30.00', now()),
       ('plan.premium.yearly.price', '25.00', now())
ON CONFLICT (setting_key) DO UPDATE
    SET value = EXCLUDED.value, updated_at = now();

-- Eski tek anahtar artık okunmuyor (yalnız kod tarafında geriye dönük yedek
-- olarak duruyor). Karışıklık yaratmasın diye siliniyor.
DELETE FROM app_settings WHERE setting_key = 'plan.pro.price';
