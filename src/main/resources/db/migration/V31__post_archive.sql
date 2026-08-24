-- V31 · Gönderi ARŞİVLEME
--
-- Referansın ⋯ menüsünde "Archive" ile "Delete" AYRI iki eylemdir ve farklı
-- şeylerdir: arşiv geri alınabilir bir gizlemedir, silme kalıcıdır.
--
-- ⚠️ `deleted_at` ile TAŞINAMAZ: o kolon soft-delete'in kendisidir ve silinen
-- gönderi kullanıcıya bir daha gösterilmez. Arşivlenen gönderi ise sahibinin
-- kendi ekranında durmaya devam eder, yalnız profilinden düşer. İkisini tek
-- kolonda birleştirmek "arşivden geri al" yolunu kapatırdı.
ALTER TABLE posts ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

-- Profil ızgarası "silinmemiş VE arşivlenmemiş" kümesini okur; kısmi indeks
-- tam da o sorguya çalışır.
CREATE INDEX idx_posts_author_visible
    ON posts (author_id, created_at DESC)
    WHERE deleted_at IS NULL AND archived = FALSE;

COMMENT ON COLUMN posts.archived IS
    'Sahibi gizledi: profilden düşer ama silinmez (geri alınabilir).';
