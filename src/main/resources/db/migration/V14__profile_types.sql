-- V14: Bölge profil türleri — klasik akış, gömülü web sitesi ya da kullanıcı HTML'i.
--
-- ⚠️ Kullanıcı HTML'i güvenlik açısından hassastır: sunucuda temizlenir (script/iframe/
-- form/on* kaldırılır) ve istemcide **sandbox iframe (allow-same-origin YOK)** ile
-- gösterilir. Bkz. vault 05-guvenlik.
ALTER TABLE territory_profiles ADD COLUMN profile_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE territory_profiles ADD COLUMN custom_html TEXT;
ALTER TABLE territory_profiles ADD CONSTRAINT ck_territory_profiles_type
    CHECK (profile_type IN ('STANDARD', 'WEBSITE', 'HTML'));
