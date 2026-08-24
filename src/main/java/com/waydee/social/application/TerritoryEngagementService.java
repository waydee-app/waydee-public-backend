package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.social.domain.TerritoryLike;
import com.waydee.social.domain.TerritorySave;
import com.waydee.social.infrastructure.TerritoryLikeRepository;
import com.waydee.social.infrastructure.TerritorySaveRepository;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.domain.TerritoryStatus;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Dairenin KENDİSİNE yapılan etkileşimler: beğeni ve kaydetme.
 *
 * Gönderi beğenisinden ayrıdır — bir daireyi hiç gönderisi olmadan da
 * beğenebilir/kaydedebilirsin. Trend skorunun iki en ağır bileşeni buradan gelir.
 */
@Service
@RequiredArgsConstructor
public class TerritoryEngagementService {

    private final TerritoryRepository territoryRepository;
    private final TerritoryLikeRepository likeRepository;
    private final TerritorySaveRepository saveRepository;
    private final ContentAccessService contentAccessService;

    public record EngagementState(int likeCount, int saveCount, boolean liked, boolean saved) {
    }

    @Transactional
    public EngagementState like(UUID territoryId, UUID userId, boolean liked) {
        Territory t = requireVisible(territoryId, userId);
        TerritoryLike.Key key = new TerritoryLike.Key(territoryId, userId);
        boolean exists = likeRepository.existsById(key);
        if (liked && !exists) {
            likeRepository.save(new TerritoryLike(territoryId, userId));
            territoryRepository.adjustLikeCount(territoryId, 1);
        } else if (!liked && exists) {
            likeRepository.deleteById(key);
            territoryRepository.adjustLikeCount(territoryId, -1);
        }
        return state(territoryId, userId);
    }

    @Transactional
    public EngagementState save(UUID territoryId, UUID userId, boolean saved) {
        Territory t = requireVisible(territoryId, userId);
        TerritorySave.Key key = new TerritorySave.Key(territoryId, userId);
        boolean exists = saveRepository.existsById(key);
        if (saved && !exists) {
            saveRepository.save(new TerritorySave(territoryId, userId));
            territoryRepository.adjustSaveCount(territoryId, 1);
        } else if (!saved && exists) {
            saveRepository.deleteById(key);
            territoryRepository.adjustSaveCount(territoryId, -1);
        }
        return state(territoryId, userId);
    }

    @Transactional(readOnly = true)
    public EngagementState state(UUID territoryId, UUID userId) {
        Territory fresh = territoryRepository.findById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        return new EngagementState(
                fresh.getLikeCount(),
                fresh.getSaveCount(),
                userId != null && likeRepository.existsById(new TerritoryLike.Key(territoryId, userId)),
                userId != null && saveRepository.existsById(new TerritorySave.Key(territoryId, userId)));
    }

    /**
     * Beğeni/kaydetme yalnız GÖRÜLEBİLEN daireye yapılabilir.
     * Gizli hesabın dairesi takipçisi olmayana kapalıdır; engel de burada elenir
     * ({@link ContentAccessService} engeli gizlilikten önce kontrol eder).
     */
    private Territory requireVisible(UUID territoryId, UUID userId) {
        Territory t = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
        if (t.isHidden() || t.getStatus() != TerritoryStatus.ACTIVE) {
            throw ApiException.notFound("Alan bulunamadı");
        }
        if (!contentAccessService.canView(userId, t.getOwner())) {
            throw ApiException.forbidden("Bu içeriğe erişemezsin");
        }
        return t;
    }
}
