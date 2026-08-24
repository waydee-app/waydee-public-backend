-- V48 · KÖKLÜ SİLME İÇİN CASCADE (yönetici "veritabanından sil")
--
-- Kullanıcı isteği (17 Ağu 2026): "admin tarafında kullanıcılarda köklü sil
-- kısmı ekle, silince veritabanından silinsin".
--
-- ═══════════════════════════════════════════════════════════════════
-- 🔴 NEDEN MİGRATION GEREKLİ
--
-- Bugün `users` satırı HİÇ silinmiyor — silme "anonimleştirme"ydi. Bu yüzden
-- 14 tablo `users(id)`'ye **varsayılan RESTRICT** ile bağlı kalmış. Düz bir
-- `DELETE FROM users` bunların ilkinde yabancı anahtar hatasıyla düşer.
--
-- ⚠️ Alternatif "servis içinde 20 tabloyu elle sırayla sil" idi ve REDDEDİLDİ:
-- sıra bağımlılık grafiğine göre elde tutulur, yeni bir tablo eklendiğinde
-- kimse listeyi güncellemez ve silme bir gün ortada patlar. Veritabanı bu
-- grafiği zaten biliyor — bırak o çözsün.
--
-- ⚠️ Kısıt ADLARI tahmin EDİLMEZ, katalogdan okunur. `<tablo>_<kolon>_fkey`
-- yalnızca bir varsayılandır; elle adlandırılmış ya da farklı üretilmiş bir
-- kısıtta script sessizce hiçbir şey yapmazdı.
-- ═══════════════════════════════════════════════════════════════════

DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conname,
               c.conrelid::regclass          AS child_table,
               a.attname                      AS child_column
        FROM pg_constraint c
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum = c.conkey[1]
        WHERE c.contype = 'f'
          AND c.confrelid = 'users'::regclass
          -- 'a' = NO ACTION (varsayılan), 'r' = RESTRICT. CASCADE/SET NULL zaten doğru.
          AND c.confdeltype IN ('a', 'r')
          AND array_length(c.conkey, 1) = 1
          /*
           * 🔴 MALİ KAYITLAR HARİÇ — bilinçli.
           * Fatura ve satın alma kayıtları muhasebe belgesidir ve yasal
           * saklama süresine tabidir; bir hesabı silmek onları da yok etmemeli.
           * Bu iki tablo RESTRICT kalır: mali kaydı olan bir hesabın köklü
           * silinmesi veritabanı seviyesinde DE engellenir (uygulama zaten
           * önce kontrol eder ve anlaşılır bir hata verir).
           */
          AND c.conrelid::regclass::text NOT IN ('invoices', 'purchases')
    LOOP
        EXECUTE format(
            'ALTER TABLE %s DROP CONSTRAINT %I',
            fk.child_table, fk.conname);
        EXECUTE format(
            'ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES users (id) ON DELETE CASCADE',
            fk.child_table, fk.conname, fk.child_column);
        RAISE NOTICE 'CASCADE: %.% -> users(id)', fk.child_table, fk.child_column;
    END LOOP;
END $$;

COMMENT ON TABLE users IS
    'Hesaplar. Yönetici iki silme yolu sunar: (1) anonimleştirme — satır kalır, kişisel veri temizlenir; (2) köklü silme — satır ve bağlı tüm kişisel içerik CASCADE ile gider. Mali kayıtları (invoices/purchases) olan hesap köklü silinemez.';
