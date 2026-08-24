-- V43 · PAZAR YERİ REVİZYONU + MAĞAZA STÜDYOSU
--
-- İki iş bir migration'da, çünkü ikisi aynı gerçeğin iki ucu: başvuru
-- **küçülüyor** (4 alan) ve küçülen alanların yerini, kabul edilen kişinin
-- kendi **stüdyosu** alıyor.
--
-- Kullanıcı isteği (15 Ağu 2026):
--   · admin: pazar adı + kısa url üst kısım aynen kalsın, ALTINA başlangıç
--     tarihi ve "kaç gün açık kalacak" gelsin.
--   · başvuru formu: SADECE tek cümlelik tanıtım · logo · website · telefon.
--   · kabul edilen kişi ürünlerini, logosunu, videosunu, müziğini, ışık
--     rengini kendi panelinden yönetsin.

-- ═══════════════════════════════════════════════════════════════════
-- 1) PAZAR YERİ: SÜRE GÜN OLARAK
--
-- `opens_at` / `closes_at` zaten vardı ama admin iki ayrı tarih giriyordu.
-- İstenen model "başlangıç + kaç gün". Süreyi ayrı bir kolonda tutuyoruz ve
-- `closes_at`i ondan TÜRETİYORUZ (servis katmanında).
--
-- 🔴 `closes_at` NEDEN SİLİNMİYOR: pencere sorgusu (`acceptsApplications`) ve
-- veritabanı indeksleri mutlak bir ana ihtiyaç duyar; her sorguda
-- `opens_at + duration` hesaplamak indekslenemez bir ifade üretirdi. Süre
-- **girdi**, `closes_at` **türetilmiş gerçek**. Vault kuralı: "aynı gerçeğin
-- iki bağımsız alanı ayrışır" — bu yüzden türetme TEK yerde (MarketplaceService).
-- ═══════════════════════════════════════════════════════════════════
ALTER TABLE marketplaces ADD COLUMN duration_days INT;

COMMENT ON COLUMN marketplaces.duration_days IS
    'Pazarın kaç gün açık kalacağı. closes_at bundan TÜRETİLİR (opens_at + gün). Boşsa süresiz.';

-- Mevcut kayıtlar için süreyi geriye dönük doldur ki admin ekranı boş açılmasın.
UPDATE marketplaces
   SET duration_days = GREATEST(1, CEIL(EXTRACT(EPOCH FROM (closes_at - opens_at)) / 86400)::INT)
 WHERE opens_at IS NOT NULL AND closes_at IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════
-- 2) BAŞVURU KÜÇÜLÜYOR — zorunlu alanlar gevşetiliyor
--
-- Yeni form yalnız dört şey soruyor: tek cümlelik tanıtım (tagline), logo,
-- website, telefon. Dolayısıyla eskiden NOT NULL olan `description` ve
-- `category` artık gelmeyebilir.
--
-- 🔴 KOLONLAR SİLİNMİYOR, yalnız zorunluluk kalkıyor. Sebep: bu alanlarda
-- **gerçek veri var** (mevcut başvurular) ve pazar türüne göre çalışan eski
-- ekranlar (STARTUP/EVENT/LISTING) hâlâ okuyor. Silmek geri dönüşü olmayan
-- bir veri kaybı olurdu; oysa istenen şey "formda sorulmasın".
--
-- ⚠️ `title` DOKUNULMADAN kalıyor (NOT NULL): mağaza adı 3B tabelanın ve
-- liste kartının tek kaynağı. Form artık sormuyor — servis onu kullanıcının
-- görünen adından TÜRETİYOR, sahibi sonra stüdyodan değiştiriyor.
-- ═══════════════════════════════════════════════════════════════════
ALTER TABLE marketplace_listings ALTER COLUMN description DROP NOT NULL;
ALTER TABLE marketplace_listings ALTER COLUMN category    DROP NOT NULL;

-- ═══════════════════════════════════════════════════════════════════
-- 3) MAĞAZA STÜDYOSU — sahibinin 3B mağazasını özelleştirdiği ayarlar
--
-- Stant (`marketplace_listings`) ile 1:1. Ayrı tablo, çünkü:
--   · stant BAŞVURU verisidir (admin onaylar, sonra donar),
--   · stüdyo ise sahibinin İSTEDİĞİ ZAMAN değiştirdiği sunum verisidir.
-- İkisini aynı satıra koymak, her ışık rengi değişiminde başvuru satırını
-- (ve denetim alanlarını) kirletirdi.
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE marketplace_store_settings (
    listing_id       UUID PRIMARY KEY REFERENCES marketplace_listings (id) ON DELETE CASCADE,

    -- Tabelada yazan ad. Boşsa listing.title kullanılır.
    display_name     VARCHAR(90),

    -- ---- kasa arkasındaki televizyon ----
    -- ⚠️ Video `media_objects`e bağlı ve silinirse SET NULL: mağaza,
    -- kaybolmuş bir dosya yüzünden açılmamazlık etmemeli — televizyon kapanır.
    video_media_id   UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    tv_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Televizyon sesi varsayılan KAPALI: caddede yürürken 40 mağazanın sesi
    -- aynı anda açılsaydı sahne kullanılamaz olurdu.
    tv_muted         BOOLEAN     NOT NULL DEFAULT TRUE,

    -- ---- mağaza müziği ----
    music_media_id   UUID REFERENCES media_objects (id) ON DELETE SET NULL,
    music_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 0–100 arası; istemci 0..1'e böler.
    music_volume     INT         NOT NULL DEFAULT 35,

    -- ---- görsel özelleştirme ----
    light_color      VARCHAR(9)  NOT NULL DEFAULT '#ffd9a0',
    -- Işık şiddeti 0–200 (yüzde). 100 = varsayılan.
    light_intensity  INT         NOT NULL DEFAULT 100,
    accent_color     VARCHAR(9)  NOT NULL DEFAULT '#83bf6e',
    -- Cephe/tente rengi.
    facade_color     VARCHAR(9)  NOT NULL DEFAULT '#111315',

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_store_music_volume    CHECK (music_volume    BETWEEN 0 AND 100),
    CONSTRAINT ck_store_light_intensity CHECK (light_intensity BETWEEN 0 AND 200),
    CONSTRAINT ck_store_light_color     CHECK (light_color  ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT ck_store_accent_color    CHECK (accent_color ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT ck_store_facade_color    CHECK (facade_color ~ '^#[0-9a-fA-F]{6}$')
);

COMMENT ON TABLE marketplace_store_settings IS
    'Kabul edilen stant sahibinin 3B mağazasını özelleştirdiği ayarlar (televizyon, müzik, ışık).';

-- ═══════════════════════════════════════════════════════════════════
-- 4) RAF ÜRÜNLERİ — iki KAYNAKTAN beslenir
--
-- Kullanıcı isteği: "hem profildeki ürünler görünsün seçip hemen ekleyebilsin
-- rafa, hem de yeni ürünleri sadece o metaverse sürecinde ekleyebileyim."
--
-- 🔴 Bu yüzden `source` iki değerli:
--   · POST   → kullanıcının mevcut gönderisi (görsel ondan gelir, `post_id` dolu)
--   · CUSTOM → yalnız mağazaya özel, profilde görünmeyen ürün
--
-- ⚠️ Raf sırası `position` ile SABİTTİR, beğeni/tarih ile değil. Vault kuralı:
-- sıra veriyle değişirse kullanıcı dün gördüğü rafı bugün bulamaz.
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE marketplace_store_products (
    id              UUID PRIMARY KEY,
    listing_id      UUID         NOT NULL REFERENCES marketplace_listings (id) ON DELETE CASCADE,

    source          VARCHAR(10)  NOT NULL,
    -- Yalnız source='POST' iken dolu. Gönderi silinirse ürün de düşer:
    -- kaynağı olmayan bir raf ürünü görselsiz kalır ve rafta boş çerçeve olur.
    post_id         UUID REFERENCES posts (id) ON DELETE CASCADE,

    title           VARCHAR(140) NOT NULL,
    description     VARCHAR(500),
    price           NUMERIC(12, 2),
    currency        VARCHAR(3),
    product_url     VARCHAR(500),
    -- Yalnız source='CUSTOM' iken anlamlı; POST'ta görsel gönderiden gelir.
    image_media_id  UUID REFERENCES media_objects (id) ON DELETE SET NULL,

    position        INT          NOT NULL DEFAULT 0,
    visible         BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_store_product_source CHECK (source IN ('POST', 'CUSTOM')),
    -- Kaynak ile veri tutarlı olmak ZORUNDA: POST ise gönderi şart, CUSTOM ise
    -- gönderi olmamalı. Bu kural veritabanında durmalı — servis katmanındaki
    -- bir `if`, toplu içe aktarma ya da elle SQL ile kolayca atlanır.
    CONSTRAINT ck_store_product_post CHECK (
        (source = 'POST'   AND post_id IS NOT NULL) OR
        (source = 'CUSTOM' AND post_id IS NULL)
    )
);

-- Raf çizimi tek sorgu: "bu stantın görünür ürünleri, sırayla".
CREATE INDEX idx_store_products_listing ON marketplace_store_products (listing_id, position);

-- Aynı gönderi aynı rafa iki kez konmasın (kullanıcı iki kez "ekle"ye basarsa).
-- ⚠️ Kısmi indeks: CUSTOM ürünlerde post_id NULL ve NULL'lar UNIQUE'i tetiklemez,
-- ama niyeti açıkça yazmak sonradan okuyanı düşünmekten kurtarır.
CREATE UNIQUE INDEX uq_store_products_post
    ON marketplace_store_products (listing_id, post_id)
 WHERE post_id IS NOT NULL;
