-- WAYDEE V9 — profil/bölge görüntüleme takibi (raporlama) + bildirimler.

CREATE TABLE territory_views (
    id           UUID PRIMARY KEY,
    territory_id UUID        NOT NULL REFERENCES territories (id) ON DELETE CASCADE,
    viewer_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    viewed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_territory_views_terr   ON territory_views (territory_id, viewed_at DESC);
CREATE INDEX idx_territory_views_viewer ON territory_views (viewer_id, territory_id, viewed_at DESC);

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,  -- alıcı
    type         VARCHAR(20) NOT NULL,
    actor_id     UUID        REFERENCES users (id) ON DELETE CASCADE,
    territory_id UUID        REFERENCES territories (id) ON DELETE CASCADE,
    is_read      BOOLEAN     NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_type CHECK (type IN ('FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_ACCEPTED', 'PROFILE_VIEW'))
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
