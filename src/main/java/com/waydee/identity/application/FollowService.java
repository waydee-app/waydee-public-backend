package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.identity.api.dto.FollowDtos.RelationshipResponse;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.Follow;
import com.waydee.identity.domain.FollowStatus;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.FollowRepository;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.application.NotificationService;
import com.waydee.social.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Takip/arkadaşlık modülü. Açık hesap → anında takip; gizli hesap → onay bekleyen istek.
 * Gizli hesabın takipçi/takip listeleri yalnız sahibine ve kabul edilmiş takipçilerine görünür.
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.waydee.moderation.application.RestrictionService restrictionService;
    private final BlockService blockService;

    @Transactional
    public RelationshipResponse follow(UUID followerId, UUID targetId) {
        restrictionService.assertAllowed(followerId, com.waydee.moderation.domain.RestrictedAction.FOLLOW);
        if (followerId.equals(targetId)) {
            throw ApiException.badRequest("Kendini takip edemezsin");
        }
        // Engel çift yönlüdür: engelleyen de engellenen de takip başlatamaz.
        blockService.assertNotBlocked(followerId, targetId);
        User target = requireUser(targetId);
        if (followRepository.findByFollowerIdAndFolloweeId(followerId, targetId).isEmpty()) {
            FollowStatus status = target.isPrivateAccount() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
            followRepository.save(new Follow(followerId, targetId, status));
            notificationService.notify(targetId,
                    status == FollowStatus.ACCEPTED ? NotificationType.FOLLOW : NotificationType.FOLLOW_REQUEST,
                    followerId, null);
        }
        return relationship(followerId, targetId);
    }

    @Transactional
    public RelationshipResponse unfollow(UUID followerId, UUID targetId) {
        followRepository.findByFollowerIdAndFolloweeId(followerId, targetId)
                .ifPresent(followRepository::delete);
        return relationship(followerId, targetId);
    }

    @Transactional
    public void accept(UUID ownerId, UUID requesterId) {
        Follow follow = followRepository.findByFollowerIdAndFolloweeId(requesterId, ownerId)
                .filter(f -> f.getStatus() == FollowStatus.PENDING)
                .orElseThrow(() -> ApiException.notFound("İstek bulunamadı"));
        follow.setStatus(FollowStatus.ACCEPTED);
        notificationService.notify(requesterId, NotificationType.FOLLOW_ACCEPTED, ownerId, null);
    }

    @Transactional
    public void reject(UUID ownerId, UUID requesterId) {
        followRepository.findByFollowerIdAndFolloweeId(requesterId, ownerId)
                .filter(f -> f.getStatus() == FollowStatus.PENDING)
                .ifPresent(followRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> requests(UUID ownerId) {
        return summaries(followRepository.findByFolloweeIdAndStatusOrderByCreatedAtDesc(ownerId, FollowStatus.PENDING)
                .stream().map(Follow::getFollowerId).toList());
    }

    @Transactional(readOnly = true)
    public long requestCount(UUID ownerId) {
        return followRepository.countByFolloweeIdAndStatus(ownerId, FollowStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> followers(UUID targetId, UUID viewerId) {
        requireCanViewLists(targetId, viewerId);
        return summaries(followRepository.findByFolloweeIdAndStatusOrderByCreatedAtDesc(targetId, FollowStatus.ACCEPTED)
                .stream().map(Follow::getFollowerId).toList());
    }

    @Transactional(readOnly = true)
    public List<UserSummary> following(UUID targetId, UUID viewerId) {
        requireCanViewLists(targetId, viewerId);
        return summaries(followRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(targetId, FollowStatus.ACCEPTED)
                .stream().map(Follow::getFolloweeId).toList());
    }

    @Transactional(readOnly = true)
    public RelationshipResponse relationship(UUID viewerId, UUID targetId) {
        User target = requireUser(targetId);
        long followers = followRepository.countByFolloweeIdAndStatus(targetId, FollowStatus.ACCEPTED);
        long following = followRepository.countByFollowerIdAndStatus(targetId, FollowStatus.ACCEPTED);
        boolean isSelf = viewerId.equals(targetId);
        boolean isFollowing = false;
        boolean requested = false;
        boolean followsYou = false;
        if (!isSelf) {
            Optional<Follow> rel = followRepository.findByFollowerIdAndFolloweeId(viewerId, targetId);
            isFollowing = rel.map(f -> f.getStatus() == FollowStatus.ACCEPTED).orElse(false);
            requested = rel.map(f -> f.getStatus() == FollowStatus.PENDING).orElse(false);
            followsYou = followRepository.existsByFollowerIdAndFolloweeIdAndStatus(targetId, viewerId, FollowStatus.ACCEPTED);
        }
        boolean blockedByMe = !isSelf && blockService.hasBlocked(viewerId, targetId);
        boolean blocked = !isSelf && blockService.blockedBetween(viewerId, targetId);
        // Engel varsa içerik de kapalıdır (gizlilik ayarından bağımsız).
        boolean canView = isSelf || (!blocked && (!target.isPrivateAccount() || isFollowing));
        return new RelationshipResponse(followers, following, isSelf, isFollowing, requested, followsYou,
                target.isPrivateAccount(), canView, blockedByMe, blocked);
    }

    /**
     * <b>Görüntüleyen bu hesabın içeriğini görebilir mi?</b>
     *
     * <p>Kendisiyse ya da <b>kabul edilmiş</b> takipçiyse evet; engel varsa
     * gizlilik ayarından bağımsız olarak hayır.
     *
     * <p>⚠️ {@link #relationship} içindeki {@code canView} ile <b>aynı</b>
     * kural; ayrı bir metot olmasının sebebi vitrin profilinin (publicview)
     * tam ilişki nesnesine değil yalnız bu karara ihtiyaç duyması.
     */
    @Transactional(readOnly = true)
    public boolean canViewContent(UUID viewerId, UUID targetId) {
        if (viewerId.equals(targetId)) {
            return true;
        }
        if (blockService.blockedBetween(viewerId, targetId)) {
            return false;
        }
        User target = requireUser(targetId);
        return !target.isPrivateAccount()
                || followRepository.existsByFollowerIdAndFolloweeIdAndStatus(viewerId, targetId, FollowStatus.ACCEPTED);
    }

    private void requireCanViewLists(UUID targetId, UUID viewerId) {
        User target = requireUser(targetId);
        if (target.isPrivateAccount() && !viewerId.equals(targetId)
                && !followRepository.existsByFollowerIdAndFolloweeIdAndStatus(viewerId, targetId, FollowStatus.ACCEPTED)) {
            throw ApiException.forbidden("Bu hesabın listeleri gizli");
        }
    }

    private List<UserSummary> summaries(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> byId = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(UserSummary::from).toList();
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
    }
}
