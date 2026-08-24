-- V33 · ÜYELİK PLANI (Ücretsiz / Pro)
--
-- Tanıtım sayfası iki plan vaat ediyor: **Ücretsiz** (günde 1 fotoğraf,
-- fotoğraf başına 3 ürün etiketi) ve **Pro — $10/ay** (sınırsız). Bugüne kadar
-- bu yalnız metindi; arka uçta hiçbir karşılığı yoktu.
--
-- ⚠️ Plan `users` tablosunda tek kolon olarak durur, ayrı tablo açılmaz:
-- kullanıcının AYNI ANDA tek planı vardır ve her istekte okunacak. Ayrı tablo
-- her kontrolde bir JOIN daha demek olurdu.
--
-- ⚠️ Ödeme/abonelik kaydı BURADA DEĞİL: `payment` modülü ayrıdır. Bu kolon
-- "şu an hangi haklara sahip" sorusunun cevabıdır; nasıl ödendiği ayrı bir
-- sorudur. İkisini karıştırmak, iade/deneme/manuel yükseltme gibi durumlarda
-- hak kontrolünü ödeme geçmişine bağımlı kılardı.
ALTER TABLE users ADD COLUMN plan VARCHAR(10) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD CONSTRAINT ck_users_plan CHECK (plan IN ('FREE', 'PRO'));

-- Planın ne zaman verildiği: yenileme/iptal ekranları ve destek için gerekli.
ALTER TABLE users ADD COLUMN plan_since TIMESTAMPTZ;

-- Günlük kotayı sayan sorgu: "bu kullanıcının bugün açtığı gönderiler".
CREATE INDEX idx_posts_author_created_at
    ON posts (author_id, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN users.plan IS
    'FREE: günde 1 gönderi, gönderi başına 3 ürün etiketi. PRO: sınırsız.';
