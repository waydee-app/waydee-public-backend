package com.waydee.identity.domain;

import com.waydee.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Bir kullanıcının başka bir kullanıcıyı engellemesi.
 *
 * Engel **tek yönlü kurulur** (blocker → blocked) ama **çift yönlü uygulanır**:
 * iki taraf da birbirini takip edemez, mesaj atamaz, içeriğini göremez.
 * Bu yüzden kontrol sorguları her zaman iki yönü birden arar
 * ({@code UserBlockRepository.existsBetween}).
 */
@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends AuditableEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "blocker_id", nullable = false)
    private UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    private UUID blockedId;

    @Column(name = "reason", length = 200)
    private String reason;

    public UserBlock(UUID blockerId, UUID blockedId, String reason) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.reason = reason;
    }
}
