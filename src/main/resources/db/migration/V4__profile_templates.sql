-- WAYDEE V4 — Profil şablonları (template atölyesi)
-- Kullanıcı, satın aldığı her alanın (bölgenin) profil kartını farklı bir
-- şablonla gösterebilir. Şablon türleri: hazır düzenler (CLASSIC/SHOWCASE/MINIMAL)
-- ve serbest CUSTOM_HTML. Şablonlar sahibine aittir, birden çok alana atanabilir.

CREATE TABLE profile_templates (
    id          UUID PRIMARY KEY,
    owner_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(80) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    accent      VARCHAR(9),
    custom_html TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_profile_templates_type CHECK (type IN ('CLASSIC', 'SHOWCASE', 'MINIMAL', 'CUSTOM_HTML'))
);
CREATE INDEX idx_profile_templates_owner ON profile_templates (owner_id);

-- Bir profil (bölge) hangi şablonu kullanıyor. Şablon silinirse varsayılana döner.
ALTER TABLE territory_profiles
    ADD COLUMN template_id UUID REFERENCES profile_templates (id) ON DELETE SET NULL;
