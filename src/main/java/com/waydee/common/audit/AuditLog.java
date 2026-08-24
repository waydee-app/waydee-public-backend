package com.waydee.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", length = 30)
    private String actorUsername;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private Map<String, Object> detail;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private AuditLog(UUID actorId, String actorUsername, String action, String entityType,
                     String entityId, Map<String, Object> detail, String ip) {
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
        this.ip = ip;
        this.createdAt = Instant.now();
    }
}
