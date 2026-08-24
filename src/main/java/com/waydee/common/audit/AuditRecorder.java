package com.waydee.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only denetim kaydı. REQUIRES_NEW ile yazılır ki ana işlem geri
 * alınsa bile başarısız denemelerin izi kalabilsin (ör. hatalı login).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String actorUsername, String action,
                       String entityType, String entityId, Map<String, Object> detail, String ip) {
        try {
            repository.save(AuditLog.builder()
                    .actorId(actorId)
                    .actorUsername(actorUsername)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .detail(detail)
                    .ip(ip)
                    .build());
        } catch (Exception ex) {
            log.error("Denetim kaydı yazılamadı: action={}", action, ex);
        }
    }
}
