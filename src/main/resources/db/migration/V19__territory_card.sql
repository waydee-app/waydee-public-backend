-- ============================================================================
-- V19 · Zengin bölge kartı alanları
--
-- Harita kartı artık bir "mini vitrin": doğrulama rozeti, öne çıkan görsel,
-- canlı yayın bağlantısı. Hepsi opsiyoneldir — hiçbiri doldurulmasa da kart
-- eksiksiz çizilir (alanlar gizlenir, boşluk kalmaz).
-- ============================================================================

-- "Doğrulanmış Alan" rozeti. Yalnız ADMIN verir — kullanıcı kendini doğrulayamaz.
ALTER TABLE territories ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_territories_verified ON territories (verified) WHERE verified = TRUE;

-- Kartın üstündeki öne çıkan görsel + canlı yayın şeridi.
ALTER TABLE territory_profiles ADD COLUMN featured_media_id UUID REFERENCES media_objects (id);
ALTER TABLE territory_profiles ADD COLUMN live_url          VARCHAR(300);
ALTER TABLE territory_profiles ADD COLUMN live_active       BOOLEAN NOT NULL DEFAULT FALSE;

-- Kartta "Bugün Görüntülenme" sayacı gün bazlı okunur; mevcut
-- idx'ler territory+viewed_at bileşenini kapsamıyordu.
CREATE INDEX IF NOT EXISTS idx_territory_views_territory_time
    ON territory_views (territory_id, viewed_at DESC);
