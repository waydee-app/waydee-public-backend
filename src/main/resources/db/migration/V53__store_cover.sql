-- V53 — MAĞAZANIN ARKA PLAN (KAPAK) FOTOĞRAFI (24 Ağustos 2026).
--
-- Kullanıcı talimatı: *"mağaza arka planı fotoğrafı ekleme geliştirmesini
-- mağaza düzenleme kısmına getir"* — haritadaki mağazaya tıklayınca açılan
-- panelin en üstünde duran geniş görsel budur.
--
-- ---------------------------------------------------------------------------
-- 🔴 NEDEN `territories` ÜSTÜNDE, `users` ÜSTÜNDE DEĞİL
--
-- Kapak, açılan panelin başlığıdır ve o panel MAĞAZAYI anlatır. Kullanıcıya
-- konsaydı, mağazasını kapatan kullanıcının kapağı ortada kalır ve "profil
-- kapağı" diye ikinci bir kavram doğardı — bugün öyle bir kavram yok.
--
-- ⚠️ Medya kimliği tutulur, ADRES DEĞİL. Adresler imzalıdır ve süresi dolar
-- (MediaUrlSigner); satıra yazılan bir adres birkaç saat sonra ölürdü.
ALTER TABLE territories ADD COLUMN IF NOT EXISTS store_cover_media_id UUID;

COMMENT ON COLUMN territories.store_cover_media_id IS
    'Mağaza panelinin üstündeki geniş görsel. NULL → panel renkli degrade çizer (V53).';

-- ⚠️ Yabancı anahtar YOK ve bu bilinçli: şemadaki diğer medya alanları
-- (`users.avatar_media_id`, `collections.cover_media_id`) de kimliği düz
-- tutar. Sahiplik kapısı uygulamadadır (`MediaService.assertOwnedBy`) —
-- başkasının medyasını kapak yapma denemesi orada durur.
