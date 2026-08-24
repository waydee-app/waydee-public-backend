package com.waydee.identity.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Kullanıcının bir sosyal medya hesabı (platform başına en fazla bir kayıt).
 *
 * {@code value} kullanıcının girdiği ham değerdir (tam URL ya da kullanıcı adı);
 * dışarı verilirken servis katmanında tam URL'ye çevrilir. Ham hâlin saklanması,
 * kullanıcı düzenleme ekranını açtığında yazdığını aynen görmesini sağlar.
 */
@Entity
@Table(name = "user_social_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialLink extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private SocialPlatform platform;

    @Column(name = "value", nullable = false, length = 200)
    @Setter
    private String value;

    @Column(name = "position", nullable = false)
    @Setter
    private int position;

    public UserSocialLink(UUID userId, SocialPlatform platform, String value, int position) {
        this.userId = userId;
        this.platform = platform;
        this.value = value;
        this.position = position;
    }
}
