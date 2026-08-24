package com.waydee.social.infrastructure;

import com.waydee.social.domain.EventRsvp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

    Optional<EventRsvp> findByPostIdAndUserId(UUID postId, UUID userId);

    List<EventRsvp> findByPostIdInAndUserId(List<UUID> postIds, UUID userId);
}
