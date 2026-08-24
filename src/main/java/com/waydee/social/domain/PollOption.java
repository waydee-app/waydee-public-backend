package com.waydee.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/** Anket seçeneği. Oy sayısı (voteCount) denormalizedir; oy verildikçe atomik artar. */
@Entity
@Table(name = "poll_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PollOption {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "text", length = 100, nullable = false)
    private String text;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    public PollOption(Post post, String text, int position) {
        this.post = post;
        this.text = text;
        this.position = position;
        this.voteCount = 0;
    }

    public void incrementVote() {
        this.voteCount++;
    }
}
