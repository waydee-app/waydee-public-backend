-- ============================================================================
-- V18 · Bölgeler artık SÜRELİ KİRALIKTIR (varsayılan 12 ay)
--
-- Satın alma kalıcı mülkiyet değil, bir kiralama dönemidir. Her bölgenin bir
-- başlangıcı ve bitişi vardır; süresi dolan bölge EXPIRED durumuna düşer ve
-- haritadan kalkar (verisi durur, yenilenince geri gelir).
--
-- Neden ayrı `lease_started_at`: `purchased_at` ilk alım anıdır ve DEĞİŞMEZ
-- (fatura ve geçmiş ona bakar). Yenileme yapıldığında yalnız dönem kayar —
-- ilk alım tarihi korunur, "üyelik yaşı" bilgisi kaybolmaz.
-- ============================================================================

ALTER TABLE territories ADD COLUMN lease_started_at TIMESTAMPTZ;
ALTER TABLE territories ADD COLUMN expires_at       TIMESTAMPTZ;
ALTER TABLE territories ADD COLUMN lease_months     INT NOT NULL DEFAULT 12;
ALTER TABLE territories ADD COLUMN renewal_count    INT NOT NULL DEFAULT 0;

-- Geriye dönük: mevcut bölgelerin kirası satın alma anında başlamış sayılır.
-- (Kimse geçmişe dönük süresi dolmuş duruma düşmesin diye status'e dokunulmaz;
--  süresi geçmiş olanları açılıştaki süpürme işi normal akışta kapatır.)
UPDATE territories
   SET lease_started_at = purchased_at,
       expires_at       = purchased_at + INTERVAL '12 months'
 WHERE expires_at IS NULL;

ALTER TABLE territories ALTER COLUMN lease_started_at SET NOT NULL;
ALTER TABLE territories ALTER COLUMN expires_at       SET NOT NULL;

ALTER TABLE territories ADD CONSTRAINT ck_territories_lease_months
    CHECK (lease_months BETWEEN 1 AND 120);
ALTER TABLE territories ADD CONSTRAINT ck_territories_lease_window
    CHECK (expires_at > lease_started_at);

-- Yeni durum: süresi dolmuş. REVOKED (admin kaldırdı) ile karıştırılmaz —
-- ikisinin geri dönüş yolu farklıdır (yenileme vs. admin geri alma).
ALTER TABLE territories DROP CONSTRAINT ck_territories_status;
ALTER TABLE territories ADD  CONSTRAINT ck_territories_status
    CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'));

-- Süresi dolanları tarayan zamanlanmış iş yalnız aktifleri gezsin.
CREATE INDEX idx_territories_expiry ON territories (expires_at) WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------
-- Yenileme de bir ödemedir: aynı `purchases` defterine yazılır ve faturalanır.
-- `kind` olmadan ciro raporunda ilk alım ile yenileme ayırt edilemezdi.
-- ---------------------------------------------------------------------------
ALTER TABLE purchases ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'PURCHASE';
ALTER TABLE purchases ADD CONSTRAINT ck_purchases_kind
    CHECK (kind IN ('PURCHASE', 'RENEWAL'));

ALTER TABLE invoices ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'PURCHASE';
ALTER TABLE invoices ADD CONSTRAINT ck_invoices_kind
    CHECK (kind IN ('PURCHASE', 'RENEWAL'));

-- Faturada kiralama dönemi de görünür (değişmez kopya mantığı: kesildiği
-- andaki dönem saklanır, bölge sonradan yenilense bile fatura bozulmaz).
ALTER TABLE invoices ADD COLUMN lease_started_at TIMESTAMPTZ;
ALTER TABLE invoices ADD COLUMN lease_expires_at TIMESTAMPTZ;

UPDATE invoices i
   SET lease_started_at = t.lease_started_at,
       lease_expires_at = t.expires_at
  FROM territories t
 WHERE t.id = i.territory_id
   AND i.lease_started_at IS NULL;
