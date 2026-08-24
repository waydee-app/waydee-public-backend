-- V46 · BAĞLANTI TIKLAMA ÖLÇÜMÜ (profil bağlantıları)
--
-- Kullanıcı isteği (16 Ağu 2026): "link tıklama analizi inanılmaz sorunlu,
-- onu baştan sona düzelt; artık hangi ülkelerden tıklandığını da göster,
-- gerçek user bilgisi olsun."
--
-- ═══════════════════════════════════════════════════════════════════
-- 🔴 ÖNCEKİ DURUM: ÖLÇÜM HİÇ YOKTU
--
-- `profile_links.click_count` kolonu Şubat'tan beri duruyor, API'de dönüyor
-- ve arayüzde gösteriliyordu — ama **hiçbir yerde artırılmıyordu**. Diskten
-- doğrulandı: kolonu artıran tek bir sorgu, tek bir uç, tek bir istemci
-- çağrısı yok. Yani kullanıcı yıllardır **her zaman sıfır** olan bir sayıya
-- bakıyordu. Üstelik SSS metni "link clicks" ve "viewer locations"
-- analizlerini **vaat ediyordu**.
--
-- ⚠️ Bu, "bozuk ölçüm"den daha kötü bir durumdur: bozuk ölçüm fark edilir,
-- hep sıfır olan bir sayı "demek ki kimse tıklamamış" diye okunur.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE profile_link_clicks (
    id          UUID        PRIMARY KEY,

    link_id     UUID        NOT NULL REFERENCES profile_links (id) ON DELETE CASCADE,

    -- Denormalize: rapor "BENİM tüm bağlantılarım" diye soruyor. Her okumada
    -- profile_links'e JOIN atmak sıcak yolda gereksiz (V44'teki post_id ile
    -- aynı gerekçe).
    owner_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- 🔴 "Gerçek user bilgisi": tıklayan GİRİŞ YAPMIŞSA kimliği saklanır.
    -- ⚠️ NULL olabilir ve çoğu satırda NULL olacaktır — vitrini gezen
    -- ziyaretçilerin çoğu oturum açmaz. Rapor bunu "ziyaretçi" olarak gösterir,
    -- boş bir satır olarak değil.
    -- ⚠️ ON DELETE SET NULL: hesap silinince (anonimleştirme) tıklama kaydı
    -- ölçüm olarak yaşamaya devam eder, kişi bağı kopar.
    user_id     UUID        REFERENCES users (id) ON DELETE SET NULL,

    -- 🔴 ISO 3166-1 alpha-2 KODU — ülke ADI DEĞİL.
    -- Vault kuralı (83. tur): sunucudan gelen `*Label` alanları çok dilli
    -- arayüzde basılmaz. Mevcut `ClientInfo.country()` Türkçe ad döndürüyor
    -- ("Almanya") ve o yüzden burada KULLANILMIYOR; kod saklanır, adı istemci
    -- `Intl.DisplayNames` ile kendi dilinde üretir.
    -- ⚠️ VARCHAR, CHAR DEĞİL: JPA `@Column(length = 2)` varchar üretir ve
    -- Hibernate şema doğrulaması CHAR'ı `bpchar` görüp açılışta uygulamayı
    -- düşürür ("found [bpchar], but expecting [varchar(2)]"). Yerelde ölçüldü.
    country     VARCHAR(2),

    -- ⚠️ Tekil ziyaretçi sayımı ve tekrar bastırma için: IP + tarayıcı
    -- imzasının SHA-256'sı. HAM IP SAKLANMAZ — kişisel veriyi ölçüm için
    -- tutmak gereksiz; sayabilmek için ayırt edici bir anahtar yeterli.
    visitor_key VARCHAR(64) NOT NULL,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Raporun tam sorgu şekilleri.
CREATE INDEX idx_link_clicks_owner_time ON profile_link_clicks (owner_id, created_at DESC);
CREATE INDEX idx_link_clicks_link_time  ON profile_link_clicks (link_id, created_at DESC);
-- Tekrar bastırma penceresi bu indeksle taranır (aynı ziyaretçi, aynı bağlantı).
CREATE INDEX idx_link_clicks_dedupe     ON profile_link_clicks (link_id, visitor_key, created_at DESC);

COMMENT ON TABLE profile_link_clicks IS
    'Profil bağlantısı tıklamaları. HAM OLAY saklanır (tıklama seyrektir; gösterimden farklı olarak satır patlaması yapmaz).';
COMMENT ON COLUMN profile_link_clicks.country IS
    'ISO alpha-2 KODU. Ülke ADI istemcide üretilir (Intl.DisplayNames) — sunucu tek dile mahkûm olmaz.';
COMMENT ON COLUMN profile_link_clicks.visitor_key IS
    'SHA-256(ip + user-agent). Ham IP saklanmaz; yalnız ayırt etmek için.';

-- ═══════════════════════════════════════════════════════════════════
-- ⚠️ NEDEN HAM OLAY (V44'te tam TERSİ karar verilmişti)
--
-- Etiket istatistiklerinde (V44) ham olay REDDEDİLMİŞTİ, çünkü orada
-- **gösterim** de sayılıyor: her sayfa açılışında her etiket bir satır üretir
-- ve tek popüler gönderi günde milyonlarca satır yapabilir.
--
-- Burada yalnız **tıklama** var. Tıklama, gösterimden kat kat seyrektir ve
-- kullanıcının istediği üç şey (hangi ülke · kim · ne zaman) ancak olay
-- düzeyinde saklanırsa yanıtlanabilir. Günlük toplama düşseydi "kim tıkladı"
-- sorusu kalıcı olarak cevapsız kalırdı.
-- ═══════════════════════════════════════════════════════════════════
