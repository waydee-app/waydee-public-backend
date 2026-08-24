package com.waydee.identity.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.domain.User;

import java.util.UUID;

public final class FollowDtos {

    private FollowDtos() {
    }

    public record UserSummary(UUID id, String username, String displayName, String avatarUrl) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(),
                    MediaUrls.of(user.getAvatarMediaId()));
        }
    }

    /**
     * İzleyicinin hedef kullanıcıyla ilişkisi + sayaçlar. Profil "Takip et" düğmesi ve
     * takipçi/takip sayıları bunu kullanır.
     */
    public record RelationshipResponse(
            long followerCount,
            long followingCount,
            boolean isSelf,
            boolean following,     // izleyici hedefi takip ediyor (ACCEPTED)
            boolean requested,     // izleyicinin isteği onay bekliyor (PENDING)
            boolean followsYou,    // hedef izleyiciyi takip ediyor
            boolean isPrivate,     // hedef gizli hesap mı
            boolean canViewContent,// izleyici hedefin içeriğini/listelerini görebilir mi
            boolean blockedByMe,   // izleyici hedefi engelledi mi (düğme durumu)
            boolean blocked        // iki yönden birinde engel var mı (etkileşim kapalı)
    ) {
    }
}
