-- WAYDEE V2 — coğrafi seed verisi (BİLEREK BOŞ BIRAKILDI)
--
-- Eskiden burada 10 ülke, 81 Türkiye ili ve büyük şehir ilçeleri seed ediliyordu.
-- Bu kayıtlar haritada otomatik "işaretli bölge" daireleri olarak çiziliyordu.
-- Talep üzerine kaldırıldı: yeni bir veritabanı sıfırdan ayağa kalktığında harita
-- TAMAMEN BOŞ açılmalı; hiçbir ülke/il/ilçe önceden oluşturulmamalı.
--
-- İdari bölgeler ve fiyatlandırma artık YALNIZCA admin panelinden (/regions,
-- /pricing-zones) elle tanımlanır. Buraya tekrar seed EKLEMEYİN — aksi halde her
-- temiz kurulumda harita yeniden dolar.
--
-- Not: Migration sürüm sırası korunuyor (V1 → V2 → V3). Dosya no-op olduğu için
-- Flyway çalıştırır ama hiçbir veri yazmaz.

SELECT 1;
