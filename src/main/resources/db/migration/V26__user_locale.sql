-- V26 — kullanıcı dil tercihi (2 Ağustos 2026)
--
-- Waydee artık çok dilli: tr · en · ar · de · es.
--
-- ⚠️ NULL = "tarayıcının dilini takip et". Bu bilinçli: hesabını açan
-- kullanıcıya bir dil DAYATMAK yerine cihazının dili kullanılır; ayarlardan
-- açıkça bir dil seçilirse o değer buraya yazılır ve artık tarayıcı ezmez.
-- (theme_mode / map_style ile aynı desen — ekstra tablo yok, tek kolon.)
--
-- ⚠️ Kolon CHECK'li: desteklenmeyen bir dil koduna düşmek, arayüzün sözlüğü
-- bulamayıp boş metinlerle açılması demektir.
ALTER TABLE users
    ADD COLUMN locale VARCHAR(5),
    ADD CONSTRAINT ck_users_locale CHECK (locale IS NULL OR locale IN ('tr', 'en', 'ar', 'de', 'es'));
