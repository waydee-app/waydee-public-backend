-- V50 — Haritadaki 3B mağazanın DÜZENLENEBİLİR görünümü (20 Ağu 2026).
--
-- Kullanıcı talimatı: *"mağaza edit ekranı aç, mağazaya tabela koyabilsin ve
-- renkleriyle oynayabilsin"*.
--
-- Bugüne kadar mağazayı ayıran tek şey DAİRENİN RENGİ ve ADIYDI; bina tüm
-- mağazalarda birebir aynıydı (bilinçliydi: farklı bina vermek haritayı
-- "hangi bina ne demek" bulmacasına çevirirdi — bkz. vault 18). Burada
-- değişen bina MODELİ değil, aynı modelin ÜSTÜNDEKİ iki şey: gövde rengi ve
-- kapısının üstündeki tabela.
--
-- ⚠️ Kolonlar `territories` üstünde, ayrı bir tabloda DEĞİL. Sebep: bunlar
-- mağazanın görünümü ve mağaza = bölge (kullanıcı başına tek satır). Ayrı bir
-- tablo, harita GeoJSON'unu üreten sorguya bir JOIN daha eklerdi ve tek bir
-- 1-1 ilişki için tablo açmak, `stroke_color`/`fill_color`'ın zaten burada
-- durduğu bir şemada tutarsız olurdu.
--
-- ⚠️ HEPSİ NULL'A İZİN VERİR ve varsayılanı OLAN yoktur. NULL burada
-- "kullanıcı dokunmadı" demektir ve okuma tarafında bugünkü görüntüye düşer:
-- boyanmamış bina + adın kendisi. Kolona varsayılan bir renk yazmak, mevcut
-- bütün mağazaları bir gecede sessizce boyardı.
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_sign_text        VARCHAR(24);
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_sign_color       VARCHAR(9);
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_sign_text_color  VARCHAR(9);
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_building_color   VARCHAR(9);
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_building_tint    NUMERIC(3, 2);

COMMENT ON COLUMN territories.store_sign_text IS
    'Tabela yazısı (en fazla 24 karakter). NULL → mağazanın adı yazılır (V50).';
COMMENT ON COLUMN territories.store_sign_color IS
    'Tabela zemin rengi #RRGGBB. NULL → dairenin rengi kullanılır (V50).';
COMMENT ON COLUMN territories.store_sign_text_color IS
    'Tabela yazı rengi #RRGGBB. NULL → zemine göre otomatik siyah/beyaz (V50).';
COMMENT ON COLUMN territories.store_building_color IS
    'Bina gövdesine karıştırılan renk #RRGGBB (Mapbox model-color). NULL → boyanmaz (V50).';
COMMENT ON COLUMN territories.store_building_tint IS
    'Bina renginin karışım şiddeti 0–1 (model-color-mix-intensity). NULL → 0, yani modelin kendi dokusu (V50).';
