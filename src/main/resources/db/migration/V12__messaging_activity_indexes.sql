-- V12: Mesajlaşma (DM) + aktivite akışı + keşfet indeksi.

-- ---------------------------------------------------------------- mesajlaşma
-- DM sohbetleri: katılımcı çifti sıralı (lo < hi) saklanır → get-or-create
-- yarışı UNIQUE kısıtla çözülür. Okunmamış sayaçları denormalize (posts.like_count deseni).
CREATE TABLE conversations (
    id                   UUID PRIMARY KEY,
    user_lo_id           UUID        NOT NULL REFERENCES users (id),
    user_hi_id           UUID        NOT NULL REFERENCES users (id),
    last_message_at      TIMESTAMPTZ,
    last_message_preview VARCHAR(140),
    unread_lo            INTEGER     NOT NULL DEFAULT 0,
    unread_hi            INTEGER     NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_conversations_pair UNIQUE (user_lo_id, user_hi_id),
    CONSTRAINT ck_conversations_order CHECK (user_lo_id < user_hi_id)
);
CREATE INDEX idx_conversations_lo ON conversations (user_lo_id, last_message_at DESC);
CREATE INDEX idx_conversations_hi ON conversations (user_hi_id, last_message_at DESC);

CREATE TABLE messages (
    id                UUID PRIMARY KEY,
    conversation_id   UUID          NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id         UUID          NOT NULL REFERENCES users (id),
    body              VARCHAR(2000) NOT NULL,
    client_message_id VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT        NOT NULL DEFAULT 0
);
-- Keyset (seek) sayfalama: WHERE conversation_id = ? AND created_at < ? ORDER BY created_at DESC
CREATE INDEX idx_messages_conv_created ON messages (conversation_id, created_at DESC, id DESC);

-- ---------------------------------------------------------------- aktivite akışı
-- "Son hareketler": satın alma / paylaşım / etkinlik. Görüntüleme alanları
-- (kullanıcı adı, bölge adı) yazım anında denormalize edilir → okuma joinsizdir.
-- İmzalı URL saklanmaz (7 gün bayatlar); avatar id saklanır, okurken imzalanır.
CREATE TABLE activity_events (
    id                    UUID PRIMARY KEY,
    type                  VARCHAR(30) NOT NULL,
    actor_id              UUID        NOT NULL,
    actor_username        VARCHAR(30) NOT NULL,
    actor_display_name    VARCHAR(60) NOT NULL,
    actor_avatar_media_id UUID,
    territory_id          UUID,
    territory_name        VARCHAR(140),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_created ON activity_events (created_at DESC);

-- ---------------------------------------------------------------- keşfet
-- Global herkese açık akış: silinmemiş gönderiler tarihe göre (partial index).
CREATE INDEX idx_posts_created_active ON posts (created_at DESC) WHERE deleted_at IS NULL;
