package com.waydee.moderation.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.moderation.api.dto.ModerationDtos.CreateReportRequest;
import com.waydee.moderation.api.dto.ModerationDtos.ReportResponse;
import com.waydee.moderation.domain.ReportReason;
import com.waydee.moderation.domain.ReportStatus;
import com.waydee.moderation.domain.UserReport;
import com.waydee.moderation.infrastructure.UserReportRepository;
import com.waydee.social.application.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Kullanıcı şikayetleri: oluşturma (kullanıcı) ve inceleme (admin). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserReportRepository reportRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final AuditRecorder auditRecorder;

    @Transactional
    public void create(UUID reporterId, CreateReportRequest request) {
        if (reporterId.equals(request.reportedUserId())) {
            throw ApiException.badRequest("Kendini şikayet edemezsin");
        }
        User reported = userRepository.findById(request.reportedUserId())
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        ReportReason reason;
        try {
            reason = ReportReason.valueOf(request.reason());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Geçersiz şikayet sebebi");
        }
        if (reportRepository.existsByReporterIdAndReportedUserIdAndStatusIn(
                reporterId, reported.getId(), List.of(ReportStatus.OPEN, ReportStatus.REVIEWING))) {
            throw ApiException.badRequest("Bu kullanıcı için zaten inceleme bekleyen bir şikayetin var");
        }
        if (request.evidenceMediaId() != null) {
            // Kanıt görseli şikayet edenin kendi yüklemesi olmalı.
            mediaService.assertOwnedBy(request.evidenceMediaId(), reporterId);
        }

        reportRepository.save(new UserReport(reporterId, reported.getId(), reason,
                request.description() != null && !request.description().isBlank()
                        ? request.description().trim() : null,
                request.evidenceMediaId()));
        auditRecorder.record(reporterId, null, "USER_REPORTED", "USER",
                reported.getId().toString(), Map.of("reason", reason.name()), null);
    }

    // ------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<ReportResponse> list(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<UserReport> reports = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? reportRepository.findAllByOrderByCreatedAtDesc(pageable)
                : reportRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);

        List<UUID> userIds = reports.getContent().stream()
                .flatMap(r -> java.util.stream.Stream.of(r.getReporterId(), r.getReportedUserId()))
                .distinct().toList();
        Map<UUID, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return PageResponse.from(reports, r -> {
            User reporter = users.get(r.getReporterId());
            User reported = users.get(r.getReportedUserId());
            return ReportResponse.from(r,
                    reporter != null ? UserSummary.from(reporter) : null,
                    reported != null ? UserSummary.from(reported) : null,
                    reportRepository.countAgainst(r.getReportedUserId()),
                    reported != null ? reported.getStatus().name() : null);
        });
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return reportRepository.countByStatus(ReportStatus.OPEN);
    }

    @Transactional
    public void resolve(UUID reportId, String status, String note, UUID adminId, String adminUsername) {
        UserReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Şikayet bulunamadı"));
        ReportStatus target = parseStatus(status);
        if (target == ReportStatus.REVIEWING) {
            report.review(adminId);
        } else {
            report.close(target, adminId, note);
        }
        auditRecorder.record(adminId, adminUsername, "REPORT_" + target.name(), "USER_REPORT",
                reportId.toString(), Map.of("reportedUser", report.getReportedUserId().toString()), null);
    }

    private ReportStatus parseStatus(String status) {
        try {
            return ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Geçersiz durum");
        }
    }
}
