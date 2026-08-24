-- V38 · DAİRE ARTIK BİR ÜRÜN DEĞİL, PREMIUM ÜYELİĞİN HAKKI
--
-- 🔴 Eski model: kullanıcı haritada istediği yarıçapta daire çizer, alanı km²
-- fiyatıyla çarpılır, ödeme oturumu açılır, webhook'la bölge oluşurdu.
--
-- Yeni model: **Premium** üye tek istekte, **ücretsiz**, **sabit 100 m**
-- yarıçapla mağazasını kurar. Ömrü üyeliğine bağlıdır.
--
-- ⚠️ Bu migration VERİ SİLMEZ. Geçmiş satın almalar, faturalar ve farklı
-- yarıçaplı eski bölgeler olduğu gibi kalır: para hareketi geçmişi yeniden
-- yazılamaz ve mevcut sahiplerin dairesi elinden alınamaz. Değişen yalnız
-- **bundan sonra ne oluşturulabileceğidir**.

-- ------------------------------------------------- kullanıcı başına tek mağaza
--
-- 🔴 Kural neden VERİTABANINDA: uygulama katmanı da kontrol ediyor, ama iki
-- eşzamanlı istek aynı anda "mağazan yok" görüp ikisi birden ekleyebilir.
-- Kısmi tekil indeks bu yarışı kökten kapatır.
--
-- ⚠️ Kısmi (`WHERE status <> 'REVOKED'`): admin tarafından kaldırılmış bir
-- mağaza kullanıcının yeni mağaza açmasını engellememeli.
--
-- ⚠️ Mevcut veride bir kullanıcının birden çok bölgesi olabilir. İndeks
-- oluşmazsa migration DÜŞER; bu yüzden önce fazlalıklar REVOKED yapılır —
-- en eskisi değil, **en yenisi** korunur (kullanıcının son kurduğu yer).
UPDATE territories t
SET status = 'REVOKED'
WHERE t.status <> 'REVOKED'
  AND EXISTS (
      SELECT 1 FROM territories other
      WHERE other.owner_id = t.owner_id
        AND other.status <> 'REVOKED'
        AND (other.purchased_at, other.id) > (t.purchased_at, t.id)
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_territories_one_store_per_owner
    ON territories (owner_id)
    WHERE status <> 'REVOKED';

COMMENT ON COLUMN territories.radius_m IS
    'Daire yarıçapı (m). V38''den beri YENİ mağazalar sabit 100 m ile kurulur; '
    'eski satırlar kendi yarıçaplarını korur.';
COMMENT ON COLUMN territories.price_paid IS
    'Ödenen bedel. V38''den beri yeni mağazalarda 0 — daire Premium üyeliğin '
    'hakkıdır, ayrıca ücretlendirilmez.';
