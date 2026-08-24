-- V52 — MAĞAZA KATEGORİLERİ (24 Ağustos 2026).
--
-- Kullanıcı talimatı: *"kategori ekleyeceğiz; hem kaydolduktan sonra popup
-- şeklinde kategorileri soracağız mağazası için, bu bilgiyi mağazada
-- kullanacağız, o alan ayarlar kısmında değiştirilebilir olsun; haritada
-- üstte kategori şeridi olsun; kategorileri DB'de tutalım, eklenip
-- çıkarılabilir olsun"*.
--
-- ---------------------------------------------------------------------------
-- 🔴 NEDEN AYRI TABLO (enum DEĞİL)
--
-- `store_marker_style` bir enum kolonudur ve olması gereken de budur: üç
-- tasarımın her biri KODDA ayrı bir çizim yolu demektir, veri eklemek yeni bir
-- tasarım üretmez. Kategori bunun tam tersi: yeni bir kategori hiçbir kod
-- yolu açmaz, yalnız bir SATIRDIR. Enum yapılsaydı "eklenip çıkarılabilir
-- olsun" isteği her seferinde bir migration + yeniden dağıtım demek olurdu.
--
-- 🔴 NEDEN `code` VAR (yalnız `id` yetmiyor)
--
-- Ad çevrilebilir olmalı (5 dil). Çeviri anahtarı satırın UUID'si olamaz —
-- sözlükte `storeCategory.9f3c…` yazamayız. `code` sabit, insan okunur ve
-- sözlük anahtarıdır: `storeCategory.FASHION`. Sözlükte karşılığı OLMAYAN
-- (yönetici sonradan eklediği) kategoriler `name` kolonuna düşer — bu yüzden
-- `name` de NOT NULL.
--
-- ⚠️ SİLME YOK, PASİFE ALMA VAR (`active`). Bir kategoriyi silmek, onu seçmiş
-- mağazaların satırındaki referansı da götürürdü; yönetici "moda" kategorisini
-- kaldırdığında 200 mağazanın kategorisi sessizce boşalırdı. Pasif kategori
-- seçim listelerinde çıkmaz ama onu ZATEN seçmiş mağazada durmaya devam eder.
CREATE TABLE IF NOT EXISTS store_categories (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(60)  NOT NULL,
    icon        VARCHAR(48)  NOT NULL,
    color       VARCHAR(9)   NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- AuditableEntity'nin beklediği ortak kolonlar; şemadaki diğer tablolarla aynı.
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON COLUMN store_categories.code IS
    'Sabit, çeviri sözlüğü anahtarı (storeCategory.<code>). Değiştirilemez (V52).';
COMMENT ON COLUMN store_categories.name IS
    'Sözlükte karşılığı olmayan (sonradan eklenen) kategorilerin gösterilen adı (V52).';
COMMENT ON COLUMN store_categories.icon IS
    'Phosphor ikon adı (ör. TShirt). İstemcideki beyaz listede yoksa yedek ikon çizilir (V52).';
COMMENT ON COLUMN store_categories.color IS
    'Şeritteki ikon rengi #RRGGBB (V52).';
COMMENT ON COLUMN store_categories.active IS
    'false → seçim listelerinde çıkmaz; ZATEN seçmiş mağazalarda durmaya devam eder (V52).';

CREATE INDEX IF NOT EXISTS idx_store_categories_active
    ON store_categories (active, sort_order);

-- ---------------------------------------------------------------------------
-- Mağazanın kategorisi.
--
-- ⚠️ `ON DELETE SET NULL` DEĞİL, `ON DELETE RESTRICT`: yukarıdaki gerekçe.
-- Kategori silinmez, pasife alınır; yanlışlıkla çalıştırılan bir DELETE
-- mağazaların kategorisini sessizce boşaltmak yerine patlamalıdır.
ALTER TABLE territories ADD COLUMN IF NOT EXISTS category_id UUID
    REFERENCES store_categories (id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_territories_category ON territories (category_id);

COMMENT ON COLUMN territories.category_id IS
    'Mağazanın kategorisi. NULL → kategorisiz (haritada "Diğer" gibi davranmaz, sadece süzgeçte eşleşmez) (V52).';

-- ---------------------------------------------------------------------------
-- 🔴 KULLANICIDA DA BİR KATEGORİ VAR — ve bu bir kopya DEĞİL.
--
-- Popup KAYIT SONRASI çıkıyor; o anda kullanıcının mağazası YOK (mağaza
-- Premium hakkıdır ve haritada bir yer seçmeyi gerektirir). Cevabı yazacak bir
-- `territories` satırı olmadığı için cevap kullanıcıda durur ve mağaza
-- açıldığında oraya TOHUM olarak geçer.
--
-- ⚠️ İki alan sonradan BAĞIMSIZDIR: kullanıcı mağazasının kategorisini
-- değiştirdiğinde `users.store_category_id` peşinden sürüklenmez. Tek alanla
-- idare edilseydi mağaza kategorisi kullanıcıya yazılırdı ve "mağaza açmadan
-- önce verilen cevap" ile "mağazanın bugünkü kategorisi" aynı hücreyi
-- paylaşırdı — ikisi farklı sorulardır.
ALTER TABLE users ADD COLUMN IF NOT EXISTS store_category_id UUID
    REFERENCES store_categories (id) ON DELETE RESTRICT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS store_category_asked_at TIMESTAMPTZ;

COMMENT ON COLUMN users.store_category_id IS
    'Kayıt sonrası sorulan "mağazan hangi alanda?" cevabı; mağaza açılınca tohum olur (V52).';
COMMENT ON COLUMN users.store_category_asked_at IS
    'Popup gösterildi mi? 🔴 `store_category_id` yetmez: "geç" diyen kullanıcıya popup HER açılışta yeniden çıkardı (V52).';

-- ---------------------------------------------------------------------------
-- Çekirdek kategoriler.
--
-- ⚠️ `gen_random_uuid()` KULLANILMIYOR, kimlikler SABİT yazılıyor. Sebep:
-- bu satırlar her ortamda (yerel · staging · üretim) AYNI kimliğe sahip
-- olmalı — aksi halde bir ortamda üretilmiş bir veri dökümü diğerinde
-- kategorileri kaybederdi ve testler kimliğe göre satır tutturamazdı.
--
-- ⚠️ `ON CONFLICT DO NOTHING`: migration bir kez koşar ama seed'in yeniden
-- çalıştırılabilir olması, aynı kodun bootstrap tarafından da güvenle
-- çağrılabilmesi demektir.
INSERT INTO store_categories (id, code, name, icon, color, sort_order, active) VALUES
    ('a1000000-0000-4000-8000-000000000001', 'FASHION',  'Moda',           'TShirt',       '#EC4899',  1, TRUE),
    ('a1000000-0000-4000-8000-000000000002', 'TECH',     'Teknoloji',      'DeviceMobile', '#3B82F6',  2, TRUE),
    ('a1000000-0000-4000-8000-000000000003', 'FOOD',     'Yiyecek',        'ForkKnife',    '#F97316',  3, TRUE),
    ('a1000000-0000-4000-8000-000000000004', 'ART',      'Sanat',          'PaintBrush',   '#A855F7',  4, TRUE),
    ('a1000000-0000-4000-8000-000000000005', 'SPORT',    'Spor',           'SoccerBall',   '#22C55E',  5, TRUE),
    ('a1000000-0000-4000-8000-000000000006', 'BEAUTY',   'Güzellik',       'Sparkle',      '#F43F5E',  6, TRUE),
    ('a1000000-0000-4000-8000-000000000007', 'HOME',     'Ev & Yaşam',     'House',        '#14B8A6',  7, TRUE),
    ('a1000000-0000-4000-8000-000000000008', 'MUSIC',    'Müzik',          'MusicNotes',   '#8B5CF6',  8, TRUE),
    ('a1000000-0000-4000-8000-000000000009', 'TRAVEL',   'Seyahat',        'AirplaneTilt', '#0EA5E9',  9, TRUE),
    ('a1000000-0000-4000-8000-00000000000a', 'SERVICE',  'Hizmet',         'Briefcase',    '#64748B', 10, TRUE),
    ('a1000000-0000-4000-8000-00000000000b', 'OTHER',    'Diğer',          'DotsThreeCircle', '#94A3B8', 99, TRUE)
ON CONFLICT (code) DO NOTHING;
