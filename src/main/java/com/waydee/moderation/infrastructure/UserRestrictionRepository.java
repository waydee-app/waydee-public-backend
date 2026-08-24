package com.waydee.moderation.infrastructure;

import com.waydee.moderation.domain.RestrictedAction;
import com.waydee.moderation.domain.UserRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRestrictionRepository extends JpaRepository<UserRestriction, UUID> {

    List<UserRestriction> findByUserId(UUID userId);

    Optional<UserRestriction> findByUserIdAndAction(UUID userId, RestrictedAction action);

    void deleteByUserIdAndAction(UUID userId, RestrictedAction action);
}
