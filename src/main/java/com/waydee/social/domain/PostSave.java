package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * <b>Gönderi kaydetme</b> (yer imi).
 *
 * <p>🔴 <b>Tablo V21'den beri vardı ama hiçbir kod ona dokunmuyordu</b> —
 * `post_saves` ve `posts.save_count` boş duruyordu; kaydetme yalnız
 * <b>bölgeler</b> için yazılmıştı ({@link TerritorySave}). Bu sınıf o boşluğu
 * kapatır.
 *
 * <p>⚠️ Beğeniyle aynı desen: bileşik birincil anahtar (post + kullanıcı), ayrı
 * kimlik kolonu yok. Aynı gönderiyi ikinci kez kaydetmek veritabanı düzeyinde
 * imkânsızdır; sayaç bu yüzden hiçbir yarışta ikiye çıkamaz.
 *
 * <p>⚠️ Zaman kolonu <b>{@code saved_at}</b> (beğenideki gibi
 * {@code created_at} değil) — tablo V21'de böyle tanımlandı ve "kaydettiklerim"
 * listesi bu kolona göre sıralanıyor.
 */
@Entity
@Table(name = "post_saves")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostSave {

    @EmbeddedId
    private PostSaveId id;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;

    public PostSave(UUID postId, UUID userId) {
        this.id = new PostSaveId(postId, userId);
        this.savedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class PostSaveId implements Serializable {

        @Column(name = "post_id")
        private UUID postId;

        @Column(name = "user_id")
        private UUID userId;

        public PostSaveId(UUID postId, UUID userId) {
            this.postId = postId;
            this.userId = userId;
        }
    }
}
