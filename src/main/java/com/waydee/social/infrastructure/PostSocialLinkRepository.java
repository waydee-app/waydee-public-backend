package com.waydee.social.infrastructure;

import com.waydee.social.domain.PostSocialLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostSocialLinkRepository extends JpaRepository<PostSocialLink, UUID> {

    List<PostSocialLink> findByPostIdOrderByPositionAsc(UUID postId);

    Optional<PostSocialLink> findByPostIdAndPlatform(UUID postId, String platform);

    long countByPostId(UUID postId);
}
