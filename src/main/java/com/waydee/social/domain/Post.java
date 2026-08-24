package com.waydee.social.domain;

import com.waydee.common.persistence.AuditableEntity;
import com.waydee.identity.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /**
     * ⚠️ NULL olabilir (V30): yeni tasarımda gönderi PROFİLE aittir, daireye
     * değil. Dolu olduğunda gönderi o dairenin akışında da görünür.
     */
    @Column(name = "territory_id")
    private UUID territoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "caption", length = 1000)
    private String caption;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Sahibi <b>arşivledi</b> (V31): profilden düşer ama silinmez.
     *
     * <p>⚠️ {@code deletedAt} ile aynı şey DEĞİLDİR — referansın ⋯ menüsünde
     * "Archive" ve "Delete" ayrı iki eylemdir; arşiv geri alınabilir.
     */
    @Column(name = "archived", nullable = false)
    private boolean archived;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PostMedia> media = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10, nullable = false)
    private PostKind kind;

    // -------- Etkinlik (EVENT) alanları
    @Column(name = "event_title", length = 140)
    private String eventTitle;

    @Column(name = "event_location", length = 140)
    private String eventLocation;

    @Column(name = "event_starts_at")
    private Instant eventStartsAt;

    @Column(name = "going_count", nullable = false)
    private int goingCount;

    @Column(name = "interested_count", nullable = false)
    private int interestedCount;

    /** Gönderi adı ("Outfit 1" gibi) — oluşturma ekranında sorulur. */
    @Column(name = "title", length = 140)
    @Setter
    private String title;

    /** Kaydetme (bookmark) sayısı — Analytics "Total Saves". Atomik güncellenir. */
    @Column(name = "save_count", nullable = false)
    private int saveCount;

    /**
     * Fotoğraf üzerindeki ürün etiketi sayısı (denormalize).
     *
     * <p>Izgara ekranı her karo için "kaç etiket var" bilgisini gösteriyor;
     * her karoda ayrı bir COUNT sorgusu klasik N+1 olurdu.
     */
    @Column(name = "tag_count", nullable = false)
    @Setter
    private int tagCount;

    // -------- Anket (POLL) seçenekleri
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<PollOption> pollOptions = new ArrayList<>();

    /** Profil gönderisi — daire yok (V30). */
    public Post(User author, String title, String caption) {
        this.author = author;
        this.title = title;
        this.caption = caption;
        this.kind = PostKind.STANDARD;
    }

    public Post(UUID territoryId, User author, String caption) {
        this.territoryId = territoryId;
        this.author = author;
        this.caption = caption;
        this.likeCount = 0;
        this.commentCount = 0;
        this.kind = PostKind.STANDARD;
        this.goingCount = 0;
        this.interestedCount = 0;
    }

    public void attachMedia(MediaObject mediaObject, int sortOrder) {
        media.add(new PostMedia(this, mediaObject, sortOrder));
    }

    public void addPollOption(String text, int position) {
        this.kind = PostKind.POLL;
        this.pollOptions.add(new PollOption(this, text, position));
    }

    public void makeEvent(String title, String location, Instant startsAt) {
        this.kind = PostKind.EVENT;
        this.eventTitle = title;
        this.eventLocation = location;
        this.eventStartsAt = startsAt;
    }

    /** Etkinlik katılım sayaçlarını ayarlar (RSVP değişiminde). */
    public void applyRsvpDelta(RsvpStatus status, int delta) {
        if (status == RsvpStatus.GOING) {
            this.goingCount += delta;
        } else {
            this.interestedCount += delta;
        }
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public void edit(String title, String caption) {
        this.title = title;
        this.caption = caption;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
