-- V15 · Bölge yönetimi (admin) — 27 Temmuz 2026
--
-- Admin panelinden bölgelerin sahipliği/görünürlüğü yönetilebilsin ve
-- "rezerve" (sahibi belirsiz, kurum bölgesi) alanlar tanımlanabilsin diye
-- territories tablosuna üç alan eklenir.
--
--  · hidden        → true ise bölge haritada ve public uçlarda GÖRÜNMEZ
--                    (silmeden gizleme; sahibi ve verisi korunur).
--  · reserved      → true ise bölge "rezerve": sahip kimliği dışarı sızmaz,
--                    haritada kurumsal etiketle çıkar. Sahibi teknik olarak
--                    admin kullanıcısıdır (owner_id NOT NULL kısıtı sürüyor).
--  · reserved_label→ rezerve bölgede gösterilecek serbest etiket
--                    (null → "Rezerve alan").

ALTER TABLE territories
    ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reserved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reserved_label VARCHAR(80);

-- Haritanın okuduğu "aktif + görünür" kümesi için kısmi indeks.
CREATE INDEX IF NOT EXISTS idx_territories_visible
    ON territories (status)
    WHERE hidden = FALSE;
