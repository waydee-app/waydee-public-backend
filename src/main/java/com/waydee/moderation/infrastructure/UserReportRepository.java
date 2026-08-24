package com.waydee.moderation.infrastructure;

import com.waydee.moderation.domain.ReportStatus;
import com.waydee.moderation.domain.UserReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserReportRepository extends JpaRepository<UserReport, UUID> {

    Page<UserReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<UserReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByReporterIdAndReportedUserIdAndStatusIn(UUID reporterId, UUID reportedUserId,
                                                           List<ReportStatus> statuses);

    long countByStatus(ReportStatus status);

    /** Bir kullanıcı hakkındaki toplam şikayet sayısı (admin listesinde rozet). */
    @Query("select count(r) from UserReport r where r.reportedUserId = :userId")
    long countAgainst(@Param("userId") UUID userId);
}
