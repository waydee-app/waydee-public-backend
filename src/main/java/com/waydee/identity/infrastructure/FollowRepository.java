package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.Follow;
import com.waydee.identity.domain.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

    boolean existsByFollowerIdAndFolloweeIdAndStatus(UUID followerId, UUID followeeId, FollowStatus status);

    long countByFolloweeIdAndStatus(UUID followeeId, FollowStatus status);

    long countByFollowerIdAndStatus(UUID followerId, FollowStatus status);

    List<Follow> findByFolloweeIdAndStatusOrderByCreatedAtDesc(UUID followeeId, FollowStatus status);

    List<Follow> findByFollowerIdAndStatusOrderByCreatedAtDesc(UUID followerId, FollowStatus status);

    /** Trend skoru — dönemde kabul edilmiş yeni takipçiler: `[followeeId, adet]`. */
    @org.springframework.data.jpa.repository.Query("""
            select f.followeeId, count(f)
            from Follow f
            where f.status = com.waydee.identity.domain.FollowStatus.ACCEPTED
              and f.createdAt >= :since
            group by f.followeeId
            """)
    List<Object[]> acceptedCountsSince(java.time.Instant since);

    /** Engelleme sırasında karşılıklı takipleri düşürmek için (durumdan bağımsız). */
    void deleteByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
}
