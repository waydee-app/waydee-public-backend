package com.waydee.social.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Bir <b>gönderiye</b> ait sosyal hesap (V32).
 *
 * <p>⚠️ {@code UserSocialLink} ile aynı şey DEĞİLDİR: o kullanıcının profilinde
 * durur, bu tek bir gönderinin altında. Aynı kişi farklı gönderilerde farklı
 * hesap gösterebilir.
 */
@Entity
@Table(name = "post_social_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostSocialLink extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Setter
    @Column(name = "value", nullable = false, length = 300)
    private String value;

    @Setter
    @Column(name = "position", nullable = false)
    private int position;

    public PostSocialLink(UUID postId, String platform, String value, int position) {
        this.id = UUID.randomUUID();
        this.postId = postId;
        this.platform = platform;
        this.value = value;
        this.position = position;
    }
}
