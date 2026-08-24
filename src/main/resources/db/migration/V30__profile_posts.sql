-- V30 · Gönderiler artık PROFİLE ait, daireye değil
--
-- Yeni tasarımda kullanıcı gönderisini ana sayfadan/profilinden oluşturuyor;
-- ortada bir daire yok. `territory_id` bu yüzden ZORUNLU olmaktan çıkıyor.
--
-- ⚠️ Kolon SİLİNMİYOR: bir gönderi hâlâ bir daireye bağlanabilir (dairenin
-- akış profilinde görünür). Yalnız "her gönderinin bir dairesi olmalı"
-- zorunluluğu kalkıyor. Mevcut satırlar aynen korunur.
ALTER TABLE posts ALTER COLUMN territory_id DROP NOT NULL;

-- Profil ızgarası "bu kullanıcının gönderileri, en yeniden eskiye" sorgusunu
-- her açılışta koşuyor; kısmi indeks silinmiş kayıtları dışarıda bırakır.
CREATE INDEX idx_posts_author_created
    ON posts (author_id, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN posts.territory_id IS
    'Opsiyonel: gönderi bir dairenin akışında da görünsün isteniyorsa dolu';
