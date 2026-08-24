package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.identity.domain.FollowStatus;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.FollowRepository;
import com.waydee.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Gizli hesap içeriği (profil, gönderi, hikaye) yalnız sahibine ve kabul edilmiş
 * takipçilerine açıktır. Liste gizliliği FollowService'te; İÇERİK gizliliği burada.
 */
@Service
@RequiredArgsConstructor
public class ContentAccessService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final com.waydee.identity.application.BlockService blockService;

    @Transactional(readOnly = true)
    public boolean canView(UUID viewerId, User owner) {
        if (owner == null) {
            return true;
        }
        // Engel her şeyin önünde gelir: açık hesap bile olsa engelli taraf içeriği göremez.
        if (viewerId != null && blockService.blockedBetween(viewerId, owner.getId())) {
            return false;
        }
        if (!owner.isPrivateAccount()) {
            return true;
        }
        if (viewerId == null) {
            return false;
        }
        return viewerId.equals(owner.getId())
                || followRepository.existsByFollowerIdAndFolloweeIdAndStatus(viewerId, owner.getId(), FollowStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public void assertCanView(UUID viewerId, User owner) {
        if (!canView(viewerId, owner)) {
            // Engel ile gizlilik aynı mesajı vermez; engelde kimin engellediği sızmaz.
            if (owner != null && viewerId != null && blockService.blockedBetween(viewerId, owner.getId())) {
                throw ApiException.forbidden("Bu kullanıcının içeriğine erişilemiyor");
            }
            throw ApiException.forbidden("Bu hesap gizli — içeriği yalnız takipçileri görebilir");
        }
    }

    @Transactional(readOnly = true)
    public void assertCanViewByOwnerId(UUID viewerId, UUID ownerId) {
        assertCanView(viewerId, userRepository.findById(ownerId).orElse(null));
    }
}
