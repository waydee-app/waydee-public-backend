-- V10: Kullanıcıya özel görünüm tercihleri (tema modu + harita stili).
-- DB-hafif tasarım: ayrı tablo/join yok, users'a iki küçük nullable kolon.
-- NULL = açık bir tercih yok → istemci sistem/varsayılan davranışa düşer.
ALTER TABLE users ADD COLUMN theme_mode VARCHAR(10);
ALTER TABLE users ADD COLUMN map_style  VARCHAR(20);

ALTER TABLE users ADD CONSTRAINT ck_users_theme_mode CHECK (theme_mode IN ('light', 'dark'));
ALTER TABLE users ADD CONSTRAINT ck_users_map_style  CHECK (map_style IN ('light', 'dark', 'streets', 'satellite'));
