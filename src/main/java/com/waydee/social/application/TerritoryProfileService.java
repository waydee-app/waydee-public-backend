package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.social.api.dto.SocialDtos.ProfileResponse;
import com.waydee.social.api.dto.SocialDtos.UpdateProfileRequest;
import com.waydee.social.domain.ProfileType;
import com.waydee.social.domain.TerritoryProfile;
import com.waydee.social.infrastructure.TerritoryProfileRepository;
import com.waydee.territory.application.event.TerritoryPurchasedEvent;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TerritoryProfileService {

    private final TerritoryProfileRepository profileRepository;
    private final TerritoryRepository territoryRepository;
    private final ContentAccessService contentAccessService;
    private final HtmlSanitizer htmlSanitizer;
    private final com.waydee.identity.application.SocialLinkService socialLinkService;
    private final MediaService mediaService;

    /**
     * Alan satın alındığı anda (aynı transaction içinde) boş sosyal profil oluşturur.
     */
    @EventListener
    @Transactional
    public void onTerritoryPurchased(TerritoryPurchasedEvent event) {
        if (profileRepository.existsById(event.territoryId())) {
            return;
        }
        Object name = ((java.util.Map<?, ?>) event.feature().get("properties")).get("name");
        profileRepository.save(new TerritoryProfile(event.territoryId(),
                name != null ? name.toString() : null));
        log.debug("Territory profili oluşturuldu: {}", event.territoryId());
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID territoryId, UUID viewerId) {
        // Gizli hesabın bölge profili yalnız sahibine ve takipçilerine açık.
        Territory territory = requireTerritory(territoryId);
        contentAccessService.assertCanView(viewerId, territory.getOwner());
        return withSocialLinks(requireProfile(territoryId), territory);
    }

    /** Profil + (açıksa) sahibinin sosyal bağlantıları. Kapalıyken sorgu hiç yapılmaz. */
    private ProfileResponse withSocialLinks(TerritoryProfile profile, Territory territory) {
        if (!profile.isShowSocialLinks()) {
            return ProfileResponse.from(profile);
        }
        return ProfileResponse.from(profile, socialLinkService.list(territory.getOwner().getId()));
    }

    @Transactional
    public ProfileResponse update(UUID territoryId, UUID userId, UpdateProfileRequest request) {
        assertOwner(territoryId, userId);
        TerritoryProfile profile = requireProfile(territoryId);

        if (request.title() != null) {
            profile.setTitle(request.title().isBlank() ? null : request.title().trim());
        }
        if (request.description() != null) {
            profile.setDescription(request.description().isBlank() ? null : request.description().trim());
        }
        if (request.website() != null) {
            // http/https doğrulanır; şemasız girilirse https:// eklenir.
            profile.setWebsite(request.website().isBlank() ? null : htmlSanitizer.normalizeWebsite(request.website()));
        }
        if (request.customHtml() != null) {
            // ⚠️ Kullanıcı HTML'i ASLA ham saklanmaz: script/iframe/form/on* temizlenir.
            profile.setCustomHtml(htmlSanitizer.sanitize(request.customHtml()));
        }
        if (request.profileType() != null) {
            ProfileType type = ProfileType.valueOf(request.profileType());
            // Tür seçiliyorsa gereken içerik dolu olmalı — boş bir "site"/"HTML" profili olmaz.
            if (type == ProfileType.WEBSITE && (profile.getWebsite() == null || profile.getWebsite().isBlank())) {
                throw ApiException.badRequest("Web sitesi türü için önce bir adres girin");
            }
            if (type == ProfileType.HTML && (profile.getCustomHtml() == null || profile.getCustomHtml().isBlank())) {
                throw ApiException.badRequest("HTML türü için içerik girin");
            }
            profile.setProfileType(type);
        }
        if (request.featuredMediaId() != null) {
            // 🔒 Başkasının medyasını kendi kartına basamaz (avatar IDOR'unun aynısı).
            mediaService.assertOwnedBy(request.featuredMediaId(), userId);
            profile.setFeaturedMediaId(request.featuredMediaId());
        }
        if (request.liveUrl() != null) {
            // Yalnız http/https — `javascript:` gibi şemalar kartta tıklanabilir olmamalı.
            profile.setLiveUrl(request.liveUrl().isBlank() ? null : htmlSanitizer.normalizeWebsite(request.liveUrl()));
        }
        if (request.liveActive() != null) {
            // Bağlantı yoksa yayın açılamaz — "Canlı" rozeti boşa çıkmasın.
            profile.setLiveActive(request.liveActive() && profile.getLiveUrl() != null);
        }
        if (request.showSocialLinks() != null) {
            profile.setShowSocialLinks(request.showSocialLinks());
        }
        return withSocialLinks(profile, requireTerritory(territoryId));
    }

    public Territory requireTerritory(UUID territoryId) {
        return territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Alan bulunamadı"));
    }

    public void assertOwner(UUID territoryId, UUID userId) {
        Territory territory = requireTerritory(territoryId);
        if (!territory.getOwner().getId().equals(userId)) {
            throw ApiException.forbidden("Bu alanın sahibi değilsiniz");
        }
    }

    private TerritoryProfile requireProfile(UUID territoryId) {
        return profileRepository.findById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Profil bulunamadı"));
    }
}
