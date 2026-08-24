-- WAYDEE V8 — Takip/arkadaşlık modülü + gizli/açık hesap.
-- Açık hesabı takip → anında ACCEPTED. Gizli hesabı takip → PENDING (onay ister).

ALTER TABLE users ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE follows (
    id          UUID PRIMARY KEY,
    follower_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followee_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status      VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_follows UNIQUE (follower_id, followee_id),
    CONSTRAINT ck_follows_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT ck_follows_self CHECK (follower_id <> followee_id)
);
CREATE INDEX idx_follows_followee ON follows (followee_id, status);
CREATE INDEX idx_follows_follower ON follows (follower_id, status);
