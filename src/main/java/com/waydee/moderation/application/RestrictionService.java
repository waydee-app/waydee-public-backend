package com.waydee.moderation.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.moderation.domain.RestrictedAction;
import com.waydee.moderation.domain.UserRestriction;
import com.waydee.moderation.infrastructure.UserRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kullanıcı bazlı eylem kısıtlamaları. Kontrol **sunucuda** yapılır —
 * istemcinin düğmeyi gizlemesi güvenlik sayılmaz.
 */
@Service
@RequiredArgsConstructor
public class RestrictionService {

    private final UserRestrictionRepository repository;

    /** Kısıtlıysa 403 fırlatır. Servislerin başında çağrılır. */
    @Transactional(readOnly = true)
    public void assertAllowed(UUID userId, RestrictedAction action) {
        if (userId == null) {
            return;
        }
        repository.findByUserIdAndAction(userId, action)
                .filter(UserRestriction::isActive)
                .ifPresent(r -> {
                    throw new ApiException(ErrorCode.FORBIDDEN, message(action, r.getReason()));
                });
    }

    @Transactional(readOnly = true)
    public List<UserRestriction> listActive(UUID userId) {
        return repository.findByUserId(userId).stream().filter(UserRestriction::isActive).toList();
    }

    @Transactional
    public UserRestriction restrict(UUID userId, RestrictedAction action, String reason,
                                    UUID adminId, Instant expiresAt) {
        UserRestriction existing = repository.findByUserIdAndAction(userId, action).orElse(null);
        if (existing != null) {
            existing.setReason(reason);
            existing.setExpiresAt(expiresAt);
            return existing;
        }
        return repository.save(new UserRestriction(userId, action, reason, adminId, expiresAt));
    }

    @Transactional
    public void lift(UUID userId, RestrictedAction action) {
        repository.deleteByUserIdAndAction(userId, action);
    }

    private String message(RestrictedAction action, String reason) {
        String base = switch (action) {
            case MESSAGE -> "Mesaj gönderme yetkiniz kısıtlandı";
            case PURCHASE -> "Alan satın alma yetkiniz kısıtlandı";
            case POST -> "Gönderi paylaşma yetkiniz kısıtlandı";
            case COMMENT -> "Yorum yazma yetkiniz kısıtlandı";
            case STORY -> "Hikaye paylaşma yetkiniz kısıtlandı";
            case FOLLOW -> "Takip etme yetkiniz kısıtlandı";
            case UPLOAD -> "Görsel yükleme yetkiniz kısıtlandı";
            case REACT -> "Beğeni/oylama yetkiniz kısıtlandı";
            case PROFILE_EDIT -> "Profil düzenleme yetkiniz kısıtlandı";
        };
        return reason != null && !reason.isBlank() ? base + ": " + reason : base;
    }
}
