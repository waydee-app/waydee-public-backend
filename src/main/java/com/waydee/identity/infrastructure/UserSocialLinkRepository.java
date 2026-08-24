package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.UserSocialLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserSocialLinkRepository extends JpaRepository<UserSocialLink, UUID> {

    List<UserSocialLink> findByUserIdOrderByPositionAsc(UUID userId);

    /** Profil ekranlarında birden çok kullanıcının bağlantısını tek sorguda çekmek için. */
    List<UserSocialLink> findByUserIdInOrderByPositionAsc(List<UUID> userIds);

    void deleteByUserId(UUID userId);
}
