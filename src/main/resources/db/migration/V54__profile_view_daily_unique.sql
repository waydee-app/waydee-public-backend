-- V54 — PROFİL GÖRÜNTÜLEME: GÜNDE BİR KEZ + BİLDİRİM KALDIRILDI (24 Ağu 2026).
--
-- Kullanıcı talimatı: *"Bir hesaba bakma bildirim özelliği kaldırılsın —
-- bildirim gelmesin, sadece istatistiklerde görünsün. Ayrıca günde ben 10 kere
-- baktıysam bu 10 kere sayılmasın, 1 kere sayılsın."*
--
-- ---------------------------------------------------------------------------
-- 🔴 NEDEN KOLON + TEKİL İNDEKS, NEDEN SADECE "SON X SAATTE VAR MI" DEĞİL
--
-- Bugünkü kısma uygulamada duruyor: yazmadan önce "son 60 dakikada bu
-- ziyaretçiden kayıt var mı" diye soruluyor. İki ayrı sorunu var:
--
-- ① **Yanlış birim.** İstenen "günde bir"; 60 dakikalık pencere aynı kişiyi
--    günde 24 kez sayabilir.
-- ② **Yarış.** Kontrol ile INSERT arasında başka bir istek araya girerse iki
--    satır birden yazılır. Uygulama seviyesindeki bir kontrol, eşzamanlı iki
--    sekmede sessizce başarısız olur — ve bu tam olarak "sayfayı yenileyip
--    duran ziyaretçi" senaryosudur.
--
-- Tekil indeks her iki sorunu da veritabanı seviyesinde bitirir: ikinci INSERT
-- **patlar** ve uygulama onu "zaten sayılmış" diye yutar. Doğruluk artık
-- zamanlamaya bağlı değil.
--
-- ⚠️ GÜN **UTC**'dir. Kullanıcının yerel günü sunucuda bilinmiyor ve bilinseydi
-- bile ziyaretçi ile sahibin saat dilimi farklı olabilirdi — "kimin günü?"
-- sorusunun tek tutarlı cevabı yok. Rapor zaten UTC gününe göre gruplanıyor
-- (`groupByDay`), yani burası şemadaki mevcut kararla aynı hizada.
ALTER TABLE territory_views ADD COLUMN IF NOT EXISTS view_day DATE;

-- Geçmiş satırlar: günü zaman damgasından türet.
UPDATE territory_views
   SET view_day = (viewed_at AT TIME ZONE 'UTC')::date
 WHERE view_day IS NULL;

-- ---------------------------------------------------------------------------
-- 🔴 TEKİLLEŞTİRME — indeksten ÖNCE, yoksa indeks kurulamaz.
--
-- Aynı (bölge, ziyaretçi, gün) için birden fazla satır varsa **en erken olan**
-- tutulur. Neden en erken: "ilk ziyaret" o günün gerçek olayıdır; sonrakiler
-- aynı olayın tekrarıdır. En geç tutulsaydı günün ilk teması kaybolurdu.
--
-- ⚠️ Bu bir VERİ KAYBIDIR ve bilinçlidir: silinen satırlar zaten sayılmaması
-- gereken tekrarlardır. Toplam görüntülenme sayısı düşecek — bu bir gerileme
-- değil, istenen düzeltmedir.
DELETE FROM territory_views tv
 WHERE tv.id NOT IN (
     SELECT DISTINCT ON (territory_id, viewer_id, view_day) id
       FROM territory_views
      ORDER BY territory_id, viewer_id, view_day, viewed_at ASC
 );

ALTER TABLE territory_views ALTER COLUMN view_day SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_territory_views_daily
    ON territory_views (territory_id, viewer_id, view_day);

COMMENT ON COLUMN territory_views.view_day IS
    'Görüntülemenin UTC günü. (bölge, ziyaretçi, gün) TEKİLDİR — aynı kişi aynı gün bir kez sayılır (V54).';

-- ---------------------------------------------------------------------------
-- 🔴 PROFİL GÖRÜNTÜLEME BİLDİRİMLERİ SİLİNİYOR
--
-- Özellik kaldırıldı: bundan sonra hiç üretilmeyecek. Mevcut satırlar
-- bırakılsaydı kullanıcının bildirim listesinde **artık desteklenmeyen** bir
-- tür süresiz duracaktı — istemci onları çizen kodu kaldırdığı için de
-- görünmez ama okunmamış sayısını şişiren "hayalet" satırlar olurlardı.
--
-- ⚠️ Enum değerinin KENDİSİ (`PROFILE_VIEW`) şemadan kaldırılmıyor: kolon
-- VARCHAR ve değeri silmek gereksiz; ileride başka bir amaçla dönerse tür
-- yerinde durur. Silinen şey veri, sözleşme değil.
DELETE FROM notifications WHERE type = 'PROFILE_VIEW';
