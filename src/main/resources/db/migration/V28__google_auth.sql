-- V28 · Google ile giriş / kayıt
--
-- Hesap artık iki yoldan açılabilir: klasik şifre (LOCAL) ya da Google (GOOGLE).
-- Kullanıcı kimliği yine `users` satırıdır — Google için ayrı bir tablo YOKTUR;
-- sağlayıcının kalıcı kimliği (`sub`) doğrudan kullanıcıya yazılır. Böylece
-- takip/mesaj/bölge gibi tüm ilişkiler tek bir kullanıcı kavramı üzerinde kalır.

ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_sub    VARCHAR(64);

ALTER TABLE users
    ADD CONSTRAINT ck_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'));

-- ⚠️ Kısmi UNIQUE: yalnız Google ile açılmış hesaplarda dolu. Tam UNIQUE olsaydı
-- NULL'lar PostgreSQL'de çakışmasa da indeks gereksiz yere tüm tabloyu tutardı.
CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- ⚠️ Google ile açılan hesabın ŞİFRESİ YOKTUR. Rastgele bir hash yazmak
-- ("hiç kullanılmayacak şifre") yanıltıcıdır: kod `passwordEncoder.matches`
-- çağırmaya devam eder ve bir gün o rastgele değer tahmin edilebilir üretilirse
-- sessiz bir giriş yolu doğar. Doğrusu kolonu NULL yapmak ve şifreli girişte
-- "bu hesabın şifresi yok" durumunu AÇIKÇA ele almaktır (AuthService).
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Google dönüşünde üretilen TEK KULLANIMLIK takas bileti de aynı jeton
-- tablosunda durur: hash'li saklama, süre, tek kullanım ve amaç ayrımı
-- burada zaten çözülmüş durumda — ikinci bir mekanizma yazmak bu güvenlik
-- özelliklerini yeniden üretmek olurdu.
ALTER TABLE verification_tokens DROP CONSTRAINT ck_verification_tokens_purpose;
ALTER TABLE verification_tokens
    ADD CONSTRAINT ck_verification_tokens_purpose
        CHECK (purpose IN ('EMAIL_VERIFY', 'EMAIL_CHANGE', 'PASSWORD_RESET', 'OAUTH_EXCHANGE'));

COMMENT ON COLUMN users.auth_provider IS 'Hesabın açıldığı yol: LOCAL (şifre) veya GOOGLE';
COMMENT ON COLUMN users.google_sub IS 'Google hesabının değişmeyen kimliği (id_token.sub); e-posta değişse bile sabittir';
