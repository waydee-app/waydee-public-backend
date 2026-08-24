package com.waydee.traffic.infrastructure;

import com.waydee.traffic.domain.LoginEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {

    Page<LoginEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LoginEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByCreatedAtAfter(Instant since);

    long countByCreatedAtAfterAndSuccessFalse(Instant since);

    /** Ülke bazlı trafik dağılımı. */
    @Query("""
            select coalesce(e.country, 'Bilinmiyor') as country, count(e) as total,
                   count(distinct e.userId) as users
            from LoginEvent e
            where e.createdAt > :since and e.success = true
            group by coalesce(e.country, 'Bilinmiyor')
            order by count(e) desc
            """)
    List<Object[]> countByCountry(@Param("since") Instant since);

    /** Cihaz dağılımı (mobil/masaüstü/tablet). */
    @Query("""
            select coalesce(e.device, 'Bilinmiyor'), count(e)
            from LoginEvent e
            where e.createdAt > :since and e.success = true
            group by coalesce(e.device, 'Bilinmiyor')
            order by count(e) desc
            """)
    List<Object[]> countByDevice(@Param("since") Instant since);

    /** Tarayıcı dağılımı. */
    @Query("""
            select coalesce(e.browser, 'Bilinmiyor'), count(e)
            from LoginEvent e
            where e.createdAt > :since and e.success = true
            group by coalesce(e.browser, 'Bilinmiyor')
            order by count(e) desc
            """)
    List<Object[]> countByBrowser(@Param("since") Instant since);

    /** Gün bazlı giriş sayısı (grafik). */
    @Query(value = """
            select date_trunc('day', created_at) as d, count(*)
            from login_events
            where created_at > :since and success = true
            group by 1 order by 1
            """, nativeQuery = true)
    List<Object[]> countByDay(@Param("since") Instant since);

    /** En aktif kullanıcılar. */
    @Query("""
            select e.username, count(e), max(e.createdAt), count(distinct coalesce(e.country, 'Bilinmiyor'))
            from LoginEvent e
            where e.createdAt > :since and e.success = true and e.userId is not null
            group by e.username
            order by count(e) desc
            """)
    List<Object[]> topUsers(@Param("since") Instant since, Pageable pageable);

    /** Tekil ziyaretçi (kullanıcı) sayısı. */
    @Query("select count(distinct e.userId) from LoginEvent e where e.createdAt > :since and e.success = true")
    long countDistinctUsers(@Param("since") Instant since);
}
