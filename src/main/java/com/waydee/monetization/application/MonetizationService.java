package com.waydee.monetization.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.monetization.api.dto.MonetizationDtos.AdminRequestResponse;
import com.waydee.monetization.api.dto.MonetizationDtos.CreateRequest;
import com.waydee.monetization.api.dto.MonetizationDtos.MyRequestResponse;
import com.waydee.monetization.domain.MonetizationRequest;
import com.waydee.monetization.domain.MonetizationStatus;
import com.waydee.monetization.infrastructure.MonetizationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * <b>Gelir başvurusu servisi.</b>
 *
 * <p>Akış: kullanıcı başvurur → yönetici görür → inceler → onaylar/reddeder.
 * Karar notu kullanıcıya geri döner.
 */
@Service
@RequiredArgsConstructor
public class MonetizationService {

    private static final List<MonetizationStatus> OPEN =
            List.of(MonetizationStatus.PENDING, MonetizationStatus.REVIEWING);

    private final MonetizationRequestRepository repository;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    /**
     * Başvuru oluşturur.
     *
     * <p>⚠️ <b>E-posta doğrulaması ŞART.</b> Gelir başvurusu bir para
     * ilişkisinin başlangıcıdır; doğrulanmamış bir adresle gelen başvuruya
     * yönetici dönüş yapamaz. Aynı kapı bölge kiralamada ve stant
     * başvurusunda da var.
     *
     * <p>⚠️ Açık başvuru kontrolü <b>hem uygulamada hem veritabanında</b>:
     * burada okunur, kısmi UNIQUE indeks de yarış koşulunu keser. Yalnız
     * uygulamada kontrol etseydik iki eşzamanlı istek iki kayıt açardı.
     */
    @Transactional
    public MyRequestResponse create(UUID userId, CreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("Başvuru için e-posta adresini doğrulamalısın");
        }
        if (repository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, OPEN).isPresent()) {
            throw ApiException.badRequest("Zaten değerlendirmede olan bir başvurun var");
        }

        MonetizationRequest entity = new MonetizationRequest(
                userId,
                trim(request.audienceNote(), 1000),
                trim(request.primaryChannel(), 300),
                trim(request.contactEmail(), 255));
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Kısmi UNIQUE indeks yakaladı — iki istek aynı anda geldi.
            throw ApiException.badRequest("Zaten değerlendirmede olan bir başvurun var");
        }

        auditRecorder.record(userId, user.getUsername(), "MONETIZATION_REQUESTED",
                "MONETIZATION_REQUEST", entity.getId().toString(), Map.of(), null);
        return MyRequestResponse.from(entity);
    }

    /** Kullanıcının en son başvurusu; hiç yoksa "başvurabilirsin" kabuğu. */
    @Transactional(readOnly = true)
    public MyRequestResponse mine(UUID userId) {
        return repository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(MyRequestResponse::from)
                .orElseGet(MyRequestResponse::none);
    }

    // ------------------------------------------------------------- yönetim

    @Transactional(readOnly = true)
    public PageResponse<AdminRequestResponse> list(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        Page<MonetizationRequest> result = parseStatus(status)
                .map(s -> repository.findByStatusOrderByCreatedAtDesc(s, pageable))
                .orElseGet(() -> repository.findAllByOrderByCreatedAtDesc(pageable));
        return PageResponse.from(result, this::toAdminRow);
    }

    /** Sol menüdeki bekleyen rozeti. */
    @Transactional(readOnly = true)
    public long pendingCount() {
        return repository.countByStatus(MonetizationStatus.PENDING);
    }

    /**
     * Kararı uygular.
     *
     * <p>⚠️ Sonuçlanmış başvuru <b>yeniden karara bağlanamaz</b>: aksi halde
     * bir yönetici diğerinin kararını sessizce ezer ve denetim izi anlamsızlaşır.
     */
    @Transactional
    public AdminRequestResponse decide(UUID adminId, UUID requestId, String status, String note) {
        MonetizationRequest entity = repository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Başvuru bulunamadı"));
        MonetizationStatus target = parseStatus(status)
                .orElseThrow(() -> ApiException.badRequest("Geçersiz durum"));

        if (!entity.isOpen()) {
            throw ApiException.badRequest("Bu başvuru zaten sonuçlandırılmış");
        }

        if (target == MonetizationStatus.REVIEWING) {
            entity.markReviewing(adminId);
        } else if (target == MonetizationStatus.APPROVED || target == MonetizationStatus.REJECTED) {
            entity.decide(target, adminId, trim(note, 1000));
        } else {
            throw ApiException.badRequest("Bu duruma geçilemez");
        }

        auditRecorder.record(adminId, null, "MONETIZATION_DECIDED",
                "MONETIZATION_REQUEST", requestId.toString(),
                Map.of("status", target.name()), null);
        return toAdminRow(entity);
    }

    // ------------------------------------------------------------ yardımcı

    private AdminRequestResponse toAdminRow(MonetizationRequest r) {
        User user = userRepository.findById(r.getUserId()).orElse(null);
        return new AdminRequestResponse(
                r.getId(),
                user != null ? UserSummary.from(user) : null,
                user != null ? user.getEmail() : null,
                user != null ? user.effectivePlan().name() : null,
                r.getStatus().name(),
                r.getAudienceNote(),
                r.getPrimaryChannel(),
                r.getContactEmail(),
                r.getDecisionNote(),
                r.getHandledAt(),
                r.getCreatedAt());
    }

    private Optional<MonetizationStatus> parseStatus(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MonetizationStatus.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** ⚠️ Kesme sunucuda yapılır: istemci sınırı aşarsa 500 değil, kırpılmış veri. */
    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        return v.length() > max ? v.substring(0, max) : v;
    }
}
