package com.waydee.social.infrastructure;

import com.waydee.social.domain.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {

    // flush: bekleyen PollVote insert'i önce yazılır; clear: taze fetch bayat sayaç görmez.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PollOption o set o.voteCount = o.voteCount + :delta where o.id = :id")
    void adjustVoteCount(@Param("id") UUID id, @Param("delta") int delta);
}
