-- V36 · Gelir başvurusu (monetization request)
--
-- 🔴 Analytics ekranındaki "Etkini gelire dönüştür" kartındaki düğme
-- 8 Ağu 2026'ya kadar HİÇBİR ŞEY YAPMIYORDU: `onClick` bile yoktu. Kullanıcı
-- talebi bu turda gerçek bir akışa bağlandı — kullanıcı başvurur, yönetici
-- görür ve karara bağlar.
--
-- ⚠️ Neden ayrı tablo (users'a kolon değil): başvurunun kendi yaşam döngüsü
-- (bekliyor → inceleniyor → onay/ret), kendi metni ve kendi karar notu var.
-- Kolon olarak users'a eklenseydi ikinci başvuru ilkini ezerdi ve geçmiş kaybolurdu.
CREATE TABLE monetization_requests (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL,
    -- Başvuru sırasında kullanıcının anlattıkları.
    audience_note   VARCHAR(1000),
    -- Ana içerik kanalı (Instagram/TikTok/YouTube adresi ya da kullanıcı adı).
    primary_channel VARCHAR(300),
    -- İletişim için tercih ettiği adres; boşsa hesabın e-postası kullanılır.
    contact_email   VARCHAR(255),
    -- Yöneticinin kararı ve gerekçesi.
    decision_note   VARCHAR(1000),
    handled_by      UUID,
    handled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_monetization_status
        CHECK (status IN ('PENDING', 'REVIEWING', 'APPROVED', 'REJECTED'))
);

-- ⚠️ KISMİ benzersiz indeks: bir kullanıcının aynı anda YALNIZ BİR açık
-- başvurusu olabilir, ama reddedilirse YENİDEN başvurabilir. Tam UNIQUE
-- olsaydı bir kez reddedilen kullanıcı bir daha hiç başvuramazdı
-- (pazar yeri başvurularındaki `uq_ml_owner_active` ile aynı gerekçe).
CREATE UNIQUE INDEX uq_monetization_open
    ON monetization_requests (user_id)
    WHERE status IN ('PENDING', 'REVIEWING');

-- Yönetim listesi duruma göre ve tarihe göre okur.
CREATE INDEX idx_monetization_status_created
    ON monetization_requests (status, created_at DESC);
