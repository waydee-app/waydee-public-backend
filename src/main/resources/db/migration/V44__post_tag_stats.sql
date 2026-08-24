-- V44 · ETİKET İSTATİSTİKLERİ (fotoğraf üzerindeki ürün etiketleri)
--
-- Kullanıcı isteği: "fotoğraflara etiketlediğimiz etiketlerin istatistiklerini
-- göreceğimiz bir geliştirme yap; paylaşılan fotoğraflara tıklayınca da orda
-- rapor alanı olsun."
--
-- `post_tags.click_count` ZATEN VAR ama yalnız **toplam** sayaçtır: "hangi gün,
-- kaç gösterim, tıklama oranı ne?" sorularını yanıtlayamaz. Bu yüzden zaman
-- serisi ekleniyor. Toplam sayaç **kalıyor** — kart üstünde tek sayı göstermek
-- için tek satırlık okuma yeterli olmalı.
--
-- ═══════════════════════════════════════════════════════════════════
-- 🔴 NEDEN HAM OLAY TABLOSU DEĞİL, GÜNLÜK TOPLAM
--
-- İlk tasarım her gösterim/tıklama için bir satır yazan `post_tag_events`
-- tablosuydu. Vazgeçildi: **gösterim** olayı tıklamadan kat kat sık üretilir —
-- vitrin sayfasındaki her fotoğrafın her etiketi, her sayfa açılışında bir
-- satır demek. Tek popüler gönderi günde milyonlarca satır üretebilir ve
-- tablo, sunduğu rapordan (günlük çubuk grafik) çok daha ayrıntılı olurdu.
--
-- Rapor zaten **gün** çözünürlüğünde. O hâlde saklama birimi de gün olmalı:
-- satır sayısı `etiket × gün` ile sınırlı kalır ve `UPSERT` ile atomik artar.
--
-- ⚠️ Bedeli bilinçli: kişi bazlı ("kim tıkladı") ve saat bazlı kırılım
-- **kaybedilir**. İkisi de istenmedi; gerekirse ayrı bir olay tablosu
-- sonradan eklenir — tersi (milyarlarca satırı sonradan toplamak) çok daha zor.
-- ═══════════════════════════════════════════════════════════════════
CREATE TABLE post_tag_daily_stats (
    tag_id      UUID        NOT NULL REFERENCES post_tags (id) ON DELETE CASCADE,
    -- Denormalize: rapor "bu gönderinin TÜM etiketleri" diye sorguluyor;
    -- her okumada post_tags'e JOIN atmak sıcak yolda gereksiz.
    -- ⚠️ Etiket silinince CASCADE ile bu satırlar da gider — rapor, artık
    -- var olmayan bir etiketi göstermemeli.
    post_id     UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,

    -- Gün, UTC. ⚠️ Yerel saat KULLANILMAZ: sunucu ve kullanıcı farklı
    -- saat dilimlerinde olabilir ve gün sınırı kayarsa aynı olay iki güne
    -- düşerdi. Arayüz gösterirken yerelleştirir.
    day         DATE        NOT NULL,

    impressions INT         NOT NULL DEFAULT 0,
    clicks      INT         NOT NULL DEFAULT 0,

    PRIMARY KEY (tag_id, day),

    CONSTRAINT ck_post_tag_stats_nonneg CHECK (impressions >= 0 AND clicks >= 0)
);

-- Raporun tam sorgu şekli: "şu gönderi · şu tarihten beri".
CREATE INDEX idx_post_tag_stats_post_day ON post_tag_daily_stats (post_id, day DESC);

COMMENT ON TABLE post_tag_daily_stats IS
    'Etiket gösterim/tıklama sayıları, GÜN bazında toplanmış. Ham olay saklanmaz — gerekçe migration başlığında.';
COMMENT ON COLUMN post_tag_daily_stats.day IS 'UTC gün. Yerelleştirme arayüzde yapılır.';
