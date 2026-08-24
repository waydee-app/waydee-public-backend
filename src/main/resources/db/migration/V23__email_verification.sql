-- V23 · E-posta doğrulama, e-posta değişimi ve şifre sıfırlama — 2 Ağustos 2026
--
-- Üç akış da AYNI jeton tablosunu kullanır; amaç `purpose` ile ayrılır.
-- Ayrı tablo açmak (verify / reset / change) aynı şemayı üç kez yazmak olurdu.
--
-- ⚠️ Jetonun kendisi DB'de DURMAZ: yalnız SHA-256 özeti saklanır (refresh
-- token'larla aynı desen). DB sızsa bile jetonlar kullanılamaz.

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

-- Mevcut hesaplar doğrulanmış sayılır: bu özellik yokken açıldılar, geriye
-- dönük kilitlemek onları kendi hesaplarından ederdi. Doğrulama zorunluluğu
-- yalnız bu migration'dan SONRA açılan hesaplar için geçerlidir.
UPDATE users
SET email_verified    = TRUE,
    email_verified_at = COALESCE(email_verified_at, created_at)
WHERE email_verified = FALSE;

CREATE TABLE IF NOT EXISTS verification_tokens (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Ham jeton değil, SHA-256 özeti (64 karakterlik hex).
    token_hash   VARCHAR(64)  NOT NULL,
    purpose      VARCHAR(20)  NOT NULL,
    -- Yalnız EMAIL_CHANGE'de dolu: doğrulanınca users.email bu değere geçer.
    -- Adresi jetonda taşımak, doğrulanmamış adresin users tablosuna hiç
    -- yazılmamasını sağlar (eski adres tıklanana dek geçerli kalır).
    target_email VARCHAR(255),
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    created_ip   VARCHAR(45),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_verification_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFY', 'EMAIL_CHANGE', 'PASSWORD_RESET'))
);

-- "Bu kullanıcının bu amaçtaki açık jetonlarını iptal et" sıcak yolda
-- (her yeni jeton üretiminde öncekiler geçersizleştirilir).
CREATE INDEX IF NOT EXISTS idx_verification_tokens_user
    ON verification_tokens (user_id, purpose) WHERE used_at IS NULL;

-- Süresi dolmuş jetonların temizliği için kısmi indeks.
CREATE INDEX IF NOT EXISTS idx_verification_tokens_expiry
    ON verification_tokens (expires_at) WHERE used_at IS NULL;
