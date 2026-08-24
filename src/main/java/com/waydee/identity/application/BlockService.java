package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.identity.api.dto.BlockDtos.BlockedUserRow;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserBlock;
import com.waydee.identity.infrastructure.FollowRepository;
import com.waydee.identity.infrastructure.UserBlockRepository;
import com.waydee.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hesap engelleme.
 *
 * Engel **tek yönlü kurulur**, **çift yönlü uygulanır**: A, B'yi engellediğinde
 * ikisi de birbirini takip edemez, mesaj atamaz ve içeriğini göremez. Bu yüzden
 * tüm kontroller {@link UserBlockRepository#existsBetween} üzerinden yapılır —
 * "kim engelledi" sorusu erişim kararında önemli değildir.
 *
 * Engelleme anında **mevcut takip ilişkileri iki yönde de silinir**; aksi hâlde
 * engellenen kişi takipçi listesinde kalır ve gizli hesap içeriğini görmeye
 * devam ederdi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    @Transactional
    public void block(UUID blockerId, UUID blockedId, String reason) {
        if (blockerId.equals(blockedId)) {
            throw ApiException.badRequest("Kendinizi engelleyemezsiniz");
        }
        User target = userRepository.findById(blockedId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return; // idempotent: zaten engelli
        }
        blockRepository.save(new UserBlock(blockerId, blockedId,
                reason != null && !reason.isBlank() ? reason.trim() : null));

        // Karşılıklı takipler düşer — engellenen kişi takipçi olarak kalmamalı.
        followRepository.deleteByFollowerIdAndFolloweeId(blockerId, blockedId);
        followRepository.deleteByFollowerIdAndFolloweeId(blockedId, blockerId);

        log.info("Kullanıcı engellendi: {} → {}", blockerId, target.getUsername());
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(blockRepository::delete);
    }

    /** Engellediğim kullanıcılar (ayarlardaki liste). */
    @Transactional(readOnly = true)
    public List<BlockedUserRow> myBlocks(UUID blockerId) {
        List<UserBlock> blocks = blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId);
        if (blocks.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = blocks.stream().map(UserBlock::getBlockedId).toList();
        Map<UUID, User> users = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return blocks.stream()
                .filter(b -> users.containsKey(b.getBlockedId()))
                .map(b -> new BlockedUserRow(UserSummary.from(users.get(b.getBlockedId())),
                        b.getReason(), b.getCreatedAt()))
                .toList();
    }

    /** Ben bu kullanıcıyı engelledim mi (profil düğmesinin durumu). */
    @Transactional(readOnly = true)
    public boolean hasBlocked(UUID blockerId, UUID blockedId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /** İki taraf arasında herhangi bir yönde engel var mı (erişim kararı). */
    @Transactional(readOnly = true)
    public boolean blockedBetween(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return blockRepository.existsBetween(a, b);
    }

    /**
     * Erişim kapısı — engelliyse isteği reddeder.
     * Mesaj bilinçli olarak nötrdür: kimin engellediği sızmaz.
     */
    public void assertNotBlocked(UUID a, UUID b) {
        if (blockedBetween(a, b)) {
            throw ApiException.forbidden("Bu kullanıcıyla etkileşim kurulamıyor");
        }
    }
}
