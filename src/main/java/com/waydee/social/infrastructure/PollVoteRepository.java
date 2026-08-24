package com.waydee.social.infrastructure;

import com.waydee.social.domain.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {

    Optional<PollVote> findByPostIdAndUserId(UUID postId, UUID userId);

    List<PollVote> findByPostIdInAndUserId(List<UUID> postIds, UUID userId);
}
