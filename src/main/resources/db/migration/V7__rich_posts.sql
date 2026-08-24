-- WAYDEE V7 — Zengin gönderi türleri: metin/foto (STANDARD) + anket (POLL) + etkinlik (EVENT).

ALTER TABLE posts ADD COLUMN kind             VARCHAR(10) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE posts ADD COLUMN event_title      VARCHAR(140);
ALTER TABLE posts ADD COLUMN event_location   VARCHAR(140);
ALTER TABLE posts ADD COLUMN event_starts_at  TIMESTAMPTZ;
ALTER TABLE posts ADD COLUMN going_count      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE posts ADD COLUMN interested_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE posts ADD CONSTRAINT ck_posts_kind CHECK (kind IN ('STANDARD', 'POLL', 'EVENT'));

-- Anket seçenekleri (oy sayısı denormalize; oy verince atomik artar)
CREATE TABLE poll_options (
    id         UUID PRIMARY KEY,
    post_id    UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    text       VARCHAR(100) NOT NULL,
    position   INTEGER      NOT NULL,
    vote_count INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_poll_options_post ON poll_options (post_id, position);

CREATE TABLE poll_votes (
    id         UUID PRIMARY KEY,
    post_id    UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    option_id  UUID        NOT NULL REFERENCES poll_options (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_poll_votes_user UNIQUE (post_id, user_id)
);

CREATE TABLE event_rsvps (
    id         UUID PRIMARY KEY,
    post_id    UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status     VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_rsvps_user UNIQUE (post_id, user_id),
    CONSTRAINT ck_event_rsvps_status CHECK (status IN ('GOING', 'INTERESTED'))
);
