-- V45 · YAPAY ZEKÂ GÖRSEL STÜDYOSU + KREDİ EKONOMİSİ
--
-- Kullanıcı isteği (16 Ağu 2026): "ai ile görsel oluşturma geliştirmesi
-- ekleyeceğiz. paketlere şimdilik sabit krediler ekleyeceğiz, proya 2000
-- premiuma 10000. yapılan işlemin kredi hesaplanması ve kredi düşümü…
-- kredi hesaplamasını mantıklı derecede yap, kullanıcıların açık bulmasına
-- izin vermeyen bir sistem olacak."
--
-- ═══════════════════════════════════════════════════════════════════
-- 🔴 NEDEN İKİ TABLO: BAKİYE + DEFTER
--
-- Tek bir `users.credits` kolonu yeterli GÖRÜNÜR ama para benzeri her sayacın
-- iki sorusu vardır: "şu an kaç?" ve "neden bu kadar?". İkincisi olmadan bir
-- destek talebini ("kredim eksildi ama görsel gelmedi") yanıtlamak imkânsızdır
-- ve iade edilip edilmediği hiçbir yerde yazmaz.
--
-- `user_credits`  → sıcak yol: tek satır okuma/atomik güncelleme.
-- `credit_ledger` → değişmez geçmiş: her hareket bir satır, `balance_after`
--                   ile birlikte. Faturanın değişmezliğiyle aynı ilke.
--
-- ⚠️ Bakiye deftere göre YENİDEN HESAPLANMAZ (SUM(delta)); milyonlarca satırı
-- her istekte toplamak sıcak yolu öldürürdü. Defter kanıttır, kaynak değil.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE user_credits (
    user_id       UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,

    -- 🔴 ASLA NEGATİF OLAMAZ. Düşüm koşullu tek bir UPDATE ile yapılır
    -- (`WHERE balance >= :cost`); kontrol-sonra-yaz iki adımı olsaydı iki
    -- eşzamanlı istek aynı bakiyeyi okuyup ikisi de harcayabilirdi.
    balance       INTEGER     NOT NULL DEFAULT 0,

    -- Ömür boyu toplamlar — rapor ve destek için; bakiyeden türetilemez.
    granted_total BIGINT      NOT NULL DEFAULT 0,
    spent_total   BIGINT      NOT NULL DEFAULT 0,

    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_user_credits_nonneg
        CHECK (balance >= 0 AND granted_total >= 0 AND spent_total >= 0)
);

COMMENT ON TABLE user_credits IS
    'Kullanıcının yapay zekâ kredi bakiyesi. Hareket geçmişi credit_ledger''dedir.';

CREATE TABLE credit_ledger (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- Pozitif = yükleme (plan hakkı, yönetici), negatif = harcama.
    delta        INTEGER     NOT NULL,
    balance_after INTEGER    NOT NULL,

    -- GRANT_PLAN | SPEND | REFUND | ADMIN_ADJUST
    reason       VARCHAR(20) NOT NULL,
    note         VARCHAR(200),

    -- ═══════════════════════════════════════════════════════════════
    -- 🔴 AÇIK KAPATAN ANAHTAR: `ref_key` TEKİLDİR.
    --
    -- Kredi ekonomisindeki iki klasik istismar da "aynı olayı iki kez
    -- saydırmak"tır:
    --   ① Aynı abonelik dönemi için iki kez yükleme (webhook tekrar gönderir —
    --      Polar'ın `order.paid` olayı yenilemede AYNI rezervasyonla gelir,
    --      78. turda bu tam olarak yaşandı).
    --   ② Başarısız bir üretimi iki kez iade ettirmek.
    -- İkisi de burada, VERİTABANI seviyesinde kapanır: yükleme anahtarı
    -- `plan:<userId>:<bitişAnı>`, iade anahtarı `refund:<generationId>`.
    -- Uygulama katmanındaki bir `if` yarış durumunda yetmezdi.
    --
    -- ⚠️ NULL olabilir (yönetici düzeltmesi gibi tekrarlanabilir hareketler);
    -- PostgreSQL'de UNIQUE birden çok NULL'a izin verir, tam istenen davranış.
    -- ═══════════════════════════════════════════════════════════════
    ref_key      VARCHAR(120) UNIQUE,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_credit_ledger_delta CHECK (delta <> 0),
    CONSTRAINT ck_credit_ledger_after CHECK (balance_after >= 0)
);

CREATE INDEX idx_credit_ledger_user_created ON credit_ledger (user_id, created_at DESC);

COMMENT ON COLUMN credit_ledger.ref_key IS
    'Tekrarı engelleyen iş anahtarı (plan:<user>:<bitiş> · refund:<generation>). Gerekçe migration başlığında.';

-- ═══════════════════════════════════════════════════════════════════
-- ÜRETİMLER
--
-- Her üretim bir satırdır ve satır ÖNCE yazılır, sonra dış servise gidilir:
-- kredi düşümü ile dış çağrı arasında bir kayıt yoksa, çağrı ortasında düşen
-- bir sunucudan sonra kullanıcının kredisi gitmiş ama hiçbir iz kalmamış olur.
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE ai_generations (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- Şimdilik tek tür: FASHION_MODEL. Sekmelerin geri kalanı ("EDITORIAL/SWAP",
    -- "POSE", "VIDEO") arayüzde **yakında** rozetiyle duruyor; tür kolonu
    -- onlar geldiğinde yeni bir tablo gerektirmesin diye şimdiden var.
    kind           VARCHAR(30) NOT NULL,

    -- QUEUED | RUNNING | SUCCEEDED | FAILED
    status         VARCHAR(20) NOT NULL,

    -- Sunucuda hesaplanan maliyet. ⚠️ İstemciden ASLA alınmaz.
    credit_cost    INTEGER     NOT NULL,
    -- İade edildi mi (başarısız üretim). Defterdeki `refund:` anahtarıyla
    -- birlikte iki katlı koruma sağlar.
    refunded       BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Kullanıcının seçtiği ayarların tamamı (cinsiyet, etnisite, saç…).
    -- JSON metin olarak saklanır: şema sık değişecek bir "form durumu"dur,
    -- sorgulanmaz, yalnız geri yüklenir ve destek için okunur.
    params_json    TEXT        NOT NULL,
    -- Modele giden nihai istem — "neden böyle çıktı" sorusunun tek yanıtı.
    prompt         TEXT,

    -- Sağlayıcının kuyruk kimliği; destek ve teşhis için.
    provider_request_id VARCHAR(80),

    -- Sonuç görseli. Depoya bizim tarafımızdan yazılır (sağlayıcının geçici
    -- adresi birkaç saatte ölür; kalıcı olması şart).
    result_media_id UUID       REFERENCES media_objects (id) ON DELETE SET NULL,

    error_message  VARCHAR(300),

    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ,

    CONSTRAINT ck_ai_generations_cost CHECK (credit_cost >= 0)
);

-- Galerinin tam sorgu şekli: "benim üretimlerim, yeniden eskiye".
CREATE INDEX idx_ai_generations_user_created ON ai_generations (user_id, created_at DESC);
-- Eşzamanlı iş sayısını sayan kapı için (kullanıcı başına açık üretim tavanı).
CREATE INDEX idx_ai_generations_user_status ON ai_generations (user_id, status);

-- Girdi ürün görselleri. ⚠️ Ayrı tablo: bir üretimde birden fazla ürün
-- olabiliyor ("bir veya birden fazla ürün eklenecek… mankene giydireceksin")
-- ve sıra önemlidir — istem ürünlere "1. ürün, 2. ürün" diye atıf yapıyor.
CREATE TABLE ai_generation_inputs (
    generation_id UUID    NOT NULL REFERENCES ai_generations (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    media_id      UUID    NOT NULL REFERENCES media_objects (id) ON DELETE CASCADE,
    PRIMARY KEY (generation_id, position)
);

COMMENT ON TABLE ai_generations IS
    'Yapay zekâ görsel üretimleri. Kredi düşümü ÖNCE yapılır, başarısızlıkta credit_ledger üzerinden tek sefer iade edilir.';
