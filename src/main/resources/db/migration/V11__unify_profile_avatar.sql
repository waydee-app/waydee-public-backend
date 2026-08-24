-- V11: "avatar = profil fotoğrafı" birleştirmesi.
-- Tek gerçek avatar users.avatar_media_id'dir (harita, header, tüm AuthorSummary
-- bunu okur). territory_profiles'taki kopya avatar ve hiçbir yerde render
-- edilmeyen kapak (cover/"arka plan") kaldırılıyor.

-- Önce eski "Profili düzenle" akışıyla set edilmiş fotoğrafları kaybetmemek için:
-- kullanıcı avatarı boşsa, sahip olduğu bir bölgenin profil avatarından backfill et.
UPDATE users u
SET avatar_media_id = tp.avatar_media_id
FROM territory_profiles tp
         JOIN territories t ON t.id = tp.territory_id
WHERE t.owner_id = u.id
  AND u.avatar_media_id IS NULL
  AND tp.avatar_media_id IS NOT NULL;

ALTER TABLE territory_profiles DROP COLUMN IF EXISTS avatar_media_id;
ALTER TABLE territory_profiles DROP COLUMN IF EXISTS cover_media_id;
