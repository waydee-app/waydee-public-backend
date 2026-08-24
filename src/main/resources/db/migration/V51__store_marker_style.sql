-- V51 — Haritadaki mağaza işaretçisinin TASARIMI (21 Ağu 2026).
--
-- Kullanıcı talimatı: *"haritada eski hale geri dönüyoruz, eğik açıdan bakma ve
-- 3D'yi kaldırıyoruz, üstten görüntüye ve konum daireleri getiriyoruz; W logosu
-- yerine kişinin profil fotoğrafı, LIVE yazan yere kişinin ismi gelecek;
-- birden fazla renk seçilebilsin, soft yanıp sönsün; mağaza açınca bu tasarımı
-- seçebilsin"*.
--
-- İşaretçi artık 3B bina değil: **halkalı profil fotoğrafı** (fotoğraftaki
-- tasarım). Halkanın RENGİ zaten var — `stroke_color` (harita verisinde
-- `color`). Yeni olan tek şey **hangi tasarımla** çizileceği.
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_marker_style VARCHAR(20);

COMMENT ON COLUMN territories.store_marker_style IS
    'Harita işaretçisinin tasarımı: PULSE | GLOW | SOFT. NULL → PULSE (V51).';

-- 🔴 NULL bırakılıyor, TOPLU GÜNCELLEME YAPILMIYOR.
--
-- "Şimdiki daireleri olan kişilere default bir şeyler gönder" isteği,
-- varsayılanı **okuma tarafında** çözerek karşılanıyor: `toFeature` NULL
-- gördüğünde PULSE yazıyor, yani mevcut bütün mağazalar yeni tasarımla
-- görünüyor. Satırlara PULSE yazmak aynı görüntüyü verir ama bir bilgiyi
-- **kaybederdi**: "kullanıcı seçti mi, yoksa varsayılan mı?" Varsayılan
-- ileride değişirse, seçmemiş olanlar yeni varsayılana taşınabilmeli.

-- ---------------------------------------------------------------------------
-- V50'nin ÖLÜ KOLONLARI SİLİNİYOR
--
-- V50, 3B binaya tabela ve gövde rengi ekliyordu. Kullanıcı hem o özelliği hem
-- 3B binayı geri aldırdı (20 Ağu); kolonlar o gün **bilerek** bırakılmıştı,
-- çünkü migration üretime uygulanmış olabilirdi ve dosyayı silmek Flyway'in
-- "applied migration not resolved locally" doğrulamasını tetikleyip backend'i
-- açılışta düşürürdü.
--
-- ⚠️ Doğru temizlik yolu buydu: V50 dosyası **yerinde duruyor** (geçmiş
-- değişmez), ölü kolonlar **yeni bir migration** ile düşüyor. Kolonlarda veri
-- yok — özellik hiç kullanılmadan geri alındı.
ALTER TABLE territories DROP COLUMN IF EXISTS store_sign_text;
ALTER TABLE territories DROP COLUMN IF EXISTS store_sign_color;
ALTER TABLE territories DROP COLUMN IF EXISTS store_sign_text_color;
ALTER TABLE territories DROP COLUMN IF EXISTS store_building_color;
ALTER TABLE territories DROP COLUMN IF EXISTS store_building_tint;
