-- V35 · PRO üyelik AYLIKTIR (bitiş tarihi)
--
-- 🔴 Sorun: `plan` bir kez PRO olunca **sonsuza kadar** PRO kalıyordu. Tanıtım
-- sayfası "$10/ay" diyordu ama arka uçta hiçbir süre yoktu: bir kez ödeyen
-- ömür boyu sınırsız kalırdı ve iade/iptal sonrası hakkı düşüren bir mekanizma
-- yoktu (yalnız adminin elle geri alması vardı, o da V34 sonrası eklendi).
--
-- Çözüm: üyeliğin bir **bitiş anı** olur. Kapı iki katlıdır:
--   1. okuma tarafı (`User#isProActive`) süresi geçmişi zaten FREE sayar,
--   2. zamanlanmış süpürme (`PlanExpiryScheduler`) satırı da FREE'ye çevirir —
--      böylece yönetim listesi ve raporlar gerçeği gösterir.
-- (Aynı ilke bölge kirasında da uygulanıyor: `LeaseExpiryScheduler`.)
ALTER TABLE users ADD COLUMN IF NOT EXISTS plan_expires_at TIMESTAMPTZ;

COMMENT ON COLUMN users.plan_expires_at IS
    'PRO üyeliğin bitiş anı. FREE hesapta NULL. PRO iken NULL ise süresi dolmuş sayılır.';

-- Mevcut PRO hesaplar: süresiz kalmasınlar. Elimizdeki tek tarih `plan_since`;
-- ondan bir ay sonrası verilir. `plan_since` yoksa (eski satır) şimdiden bir ay.
UPDATE users
SET plan_expires_at = COALESCE(plan_since, now()) + INTERVAL '1 month'
WHERE plan = 'PRO' AND plan_expires_at IS NULL;

-- Süpürme bu kolonu tarar; PRO hesap sayısı azdır ama tarama her saat koşar.
CREATE INDEX IF NOT EXISTS idx_users_plan_expires
    ON users (plan_expires_at)
    WHERE plan = 'PRO';
