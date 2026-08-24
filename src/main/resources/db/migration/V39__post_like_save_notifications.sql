-- V39 · BEĞENİ ve KAYDETME BİLDİRİMLERİ
--
-- Bildirimler bugüne kadar yalnız **ilişki** olaylarını taşıyordu (takip,
-- takip isteği, kabul, profil görüntüleme). Gönderiyle ilgili hiçbir olay
-- bildirilmiyordu: birisi gönderini beğense de kaydetse de haberin olmuyordu.
--
-- ⚠️ Kaydetme V38'de bilinçli olarak **sessiz** bırakılmıştı ("Instagram'da da
-- öyle"). Kullanıcı bunun tersini istedi: bu üründe kaydetme, beğeniden daha
-- güçlü bir sinyal (satın alma niyeti) ve sahibinin görmesi isteniyor. Karar
-- değişti, kod da değişti — gerekçesi burada kalsın ki bir dahaki turda
-- "sessiz olmalıydı" diye geri alınmasın.

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type
    CHECK (type IN ('FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_ACCEPTED', 'PROFILE_VIEW',
                    'POST_LIKE', 'POST_SAVE'));

-- 🔴 Bildirimin HANGİ gönderi için olduğu gerekli: tıklayınca gönderi detayına
-- gitmeli. `territory_id` bunu karşılamaz — gönderi bir bölgeye bağlı olsa da
-- kullanıcı "hangi fotoğrafım" sorusunun cevabını bekler.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS post_id UUID;

-- ⚠️ Gönderi silinince bildirim de silinir (CASCADE): silinmiş bir gönderiye
-- götüren bildirim, tıklandığında 404 veren ölü bir satırdır.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_post_id_fkey;
ALTER TABLE notifications ADD CONSTRAINT notifications_post_id_fkey
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE;

COMMENT ON COLUMN notifications.post_id IS
    'POST_LIKE / POST_SAVE bildiriminin gönderisi. İlişki bildirimlerinde NULL.';

-- 🔴 AYNI kişi AYNI gönderiyi beğenip geri alıp tekrar beğenirse bildirim
-- yığılmasın: (alıcı, tür, aktör, gönderi) dörtlüsü tekildir.
-- ⚠️ Kısmi indeks — yalnız gönderi bildirimlerinde. İlişki bildirimlerinde
-- `post_id` NULL'dur ve NULL'lar tekil indekste çakışmaz, ama kuralın
-- kapsamını açıkça daraltmak niyeti okunur kılar.
--
-- ⚠️ Mevcut satırlarda çakışma OLAMAZ (post_id az önce eklendi, hepsi NULL),
-- bu yüzden temizlik adımı gerekmiyor.
CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_post_actor
    ON notifications (user_id, type, actor_id, post_id)
    WHERE post_id IS NOT NULL;
