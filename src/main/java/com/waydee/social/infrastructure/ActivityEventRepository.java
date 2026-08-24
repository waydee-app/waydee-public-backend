package com.waydee.social.infrastructure;

import com.waydee.social.domain.ActivityEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    List<ActivityEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** "Son hareketler" paneli yalnız yakın geçmişi gösterir (varsayılan 10 dk). */
    List<ActivityEvent> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since, Pageable pageable);
}
