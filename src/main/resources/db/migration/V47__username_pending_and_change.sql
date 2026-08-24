-- V47 · KULLANICI ADI: BEKLEYEN SEÇİM + DEĞİŞTİRME GEÇMİŞİ
--
-- Kullanıcı isteği (17 Ağu 2026): "gmail ile giriş yapınca otomatik olarak
-- gmail adını aldı; ilk girişte bir username ekranı açılsın. Ayarlara da
-- username değiştirme ekle."
--
-- ═══════════════════════════════════════════════════════════════════
-- 🔴 NEDEN GEÇİCİ BİR BAYRAK DEĞİL, KALICI BİR KOLON
--
-- "Yeni hesap mı" bilgisini giriş yanıtında (TokenResponse) taşımak ilk
-- akla gelen çözümdü ve YANLIŞ olurdu: kullanıcı ad seçme ekranını
-- kapatıp çıkarsa bilgi kaybolur ve bir daha ASLA sorulmaz — hesap
-- kalıcı olarak `mustafaciceknote8` gibi otomatik üretilmiş bir adla
-- kalırdı. Bayrak kullanıcının seçimiyle kapanmalı, oturumla değil.
-- ═══════════════════════════════════════════════════════════════════
ALTER TABLE users
    ADD COLUMN username_pending BOOLEAN NOT NULL DEFAULT FALSE;

-- ⚠️ NULL = "hiç değiştirilmedi" ve bu bilinçli: bekleme süresi kontrolü
-- ilk değişikliği SERBEST bırakır. Google ile açılan hesabın otomatik adı
-- kullanıcının seçimi değildir; onu düzeltmek için 30 gün beklemek saçma
-- olurdu.
ALTER TABLE users
    ADD COLUMN username_changed_at TIMESTAMPTZ;

COMMENT ON COLUMN users.username_pending IS
    'Kullanıcı adı sistem tarafından üretildi ve kullanıcı henüz kendi adını seçmedi (Google ile kayıt). Arayüz bu bayrağa göre seçim ekranını açar.';
COMMENT ON COLUMN users.username_changed_at IS
    'Son kullanıcı adı değişikliği. NULL = hiç değiştirilmedi → ilk değişiklik bekleme süresine takılmaz.';

-- Mevcut Google hesapları da ad seçmeli: adları e-posta yerel kısmından
-- türetildi, yani kullanıcının seçimi değil.
-- ⚠️ Yalnız adı HÂLÂ otomatik üretilmiş görünenler değil, TÜM Google
-- hesapları işaretlenir: hangisinin adını beğendiğini bilemeyiz, sormak
-- yanlış bir adı sessizce kalıcı kılmaktan iyidir. Seçim ekranı mevcut adı
-- öneri olarak gösterir; "bu iyi" diyen tek tıkla geçer.
UPDATE users
SET username_pending = TRUE
WHERE auth_provider = 'GOOGLE'
  AND username_changed_at IS NULL;
