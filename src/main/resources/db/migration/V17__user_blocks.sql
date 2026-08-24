-- V17 · Hesap engelleme — 29 Temmuz 2026
--
-- Kullanıcı başka bir kullanıcıyı engelleyebilir. Engel **tek yönlü kurulur**
-- ama **çift yönlü uygulanır**: A, B'yi engellerse ikisi de birbirini takip
-- edemez, mesaj atamaz ve içeriğini göremez (Instagram davranışı).
--
-- Not: Şikayet (`user_reports`) ayrı bir mekanizmadır — engel kişiseldir,
-- şikayet moderasyona gider. Engelleme moderasyon kaydı üretmez.

CREATE TABLE IF NOT EXISTS user_blocks (
    id         UUID PRIMARY KEY,
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID,
    updated_by UUID,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_blocks UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_self CHECK (blocker_id <> blocked_id)
);

-- "Beni kim engelledi" ve "kimi engelledim" sorguları iki yönde de sıcak yolda.
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker ON user_blocks (blocker_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks (blocked_id);
