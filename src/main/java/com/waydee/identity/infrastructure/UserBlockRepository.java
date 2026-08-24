package com.waydee.identity.infrastructure;

import com.waydee.identity.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /**
     * İki kullanıcı arasında **herhangi bir yönde** engel var mı.
     * Uygulama tarafında engel çift yönlüdür: engellenen de engelleyene erişemez.
     */
    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.blockerId = :a and b.blockedId = :b)
               or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    /** Görüntüleyenin engel ilişkisi içinde olduğu kullanıcı kimlikleri (liste filtreleri için). */
    @Query("""
            select case when b.blockerId = :userId then b.blockedId else b.blockerId end
            from UserBlock b
            where b.blockerId = :userId or b.blockedId = :userId
            """)
    List<UUID> relatedUserIds(@Param("userId") UUID userId);
}
