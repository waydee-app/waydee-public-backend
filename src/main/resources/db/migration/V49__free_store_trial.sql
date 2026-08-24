-- V49 — Ücretsiz planın "1 aylık mağaza" denemesi (18 Ağu 2026).
--
-- Ücretsiz hesap artık haritada bir kez mağaza açabiliyor (30 gün). Hak
-- HESAP ÖMRÜNDE BİR KEZDİR; bu kolon onu işaretler.
--
-- ⚠️ Mağazanın bitiş anı BURADA DEĞİL, `territories.expires_at` içindedir.
-- Buradaki tek soru "deneme harcandı mı" — mağaza silinse bile hak geri
-- gelmemeli, yoksa sil-yeniden aç döngüsüyle süre sonsuza uzardı.
--
-- ⚠️ Mevcut satırlar NULL kalır, yani bugünkü ücretsiz kullanıcıların hepsi
-- denemeye hak kazanır. Kasıtlı: hak yeni tanımlandı, geriye dönük harcanmış
-- sayılamaz.
ALTER TABLE users ADD COLUMN IF NOT EXISTS free_store_used_at TIMESTAMPTZ;

COMMENT ON COLUMN users.free_store_used_at IS
    'Ücretsiz 1 aylık mağaza denemesinin harcandığı an; NULL = hak duruyor (V49).';
