-- WAYDEE V5 — profil şablonu (template atölyesi) özelliği talep üzerine TAMAMEN kaldırıldı.
-- V4 ile eklenen tablo ve kolon geri düşürülür. Kullanıcı/bölge verisi korunur.

ALTER TABLE territory_profiles DROP COLUMN IF EXISTS template_id;
DROP TABLE IF EXISTS profile_templates;
