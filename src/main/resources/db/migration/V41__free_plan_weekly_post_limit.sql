-- V41 · Ücretsiz planın gönderi sınırı GÜNLÜKTEN HAFTALIĞA çekildi (10 Ağu 2026)
--
-- Şema değişmiyor: sınır bir sütunda değil, `UserPlan` enum'unda ve
-- `PlanService`'te yaşıyor. Değişen tek kalıcı şey, `users.plan` sütununun
-- **açıklamasıdır** — V33 ve V37 oraya "günde 1 gönderi" yazmıştı.
--
-- 🔴 Neden ayrı bir migration: V33/V37 uygulanmış migration'lardır ve
-- geçmişe dönük düzenlenmezler (Flyway sağlaması bozulur). Yanlış bir yorumu
-- yerinde bırakmak da seçenek değil — veritabanına bakan bir sonraki kişi
-- ürünün kuralını sütun yorumundan okur ve yanlış öğrenir.

COMMENT ON COLUMN users.plan IS
    'FREE: haftada 1 gönderi (UTC pazartesi başlangıçlı takvim haftası), '
    '3 etiket. PRO: sınırsız gönderi/etiket + mavi tik. '
    'PREMIUM: PRO''nun hepsi + haritada 100 m mağaza dairesi.';
