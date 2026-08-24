-- WAYDEE V6 — Hikayeler (Instagram tarzı story). 24 saat sonra otomatik "kaybolur"
-- (expires_at ile filtrelenir; kalıcı silme gerekmez).

CREATE TABLE stories (
    id         UUID PRIMARY KEY,
    author_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    media_id   UUID        NOT NULL REFERENCES media_objects (id) ON DELETE CASCADE,
    caption    VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_stories_active ON stories (expires_at, created_at);
CREATE INDEX idx_stories_author ON stories (author_id, expires_at);

CREATE TABLE story_views (
    id        UUID PRIMARY KEY,
    story_id  UUID        NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    viewer_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_story_views UNIQUE (story_id, viewer_id)
);
CREATE INDEX idx_story_views_viewer ON story_views (viewer_id);
