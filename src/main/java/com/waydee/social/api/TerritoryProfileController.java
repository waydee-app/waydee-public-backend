package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.SocialDtos.ProfileResponse;
import com.waydee.social.api.dto.SocialDtos.UpdateProfileRequest;
import com.waydee.social.application.AnalyticsService;
import com.waydee.social.application.TerritoryProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Territory Profiles", description = "Alan sosyal profili")
@RestController
@RequestMapping("/api/v1/territories/{territoryId}/profile")
@RequiredArgsConstructor
public class TerritoryProfileController {

    private final TerritoryProfileService profileService;
    private final AnalyticsService analyticsService;

    @Operation(summary = "Alan profili")
    @GetMapping
    public ProfileResponse get(@PathVariable UUID territoryId,
                               @AuthenticationPrincipal AuthenticatedUser principal) {
        // Önce yetki (gizli hesap gate'i); görüntüleme kaydı sonra ve async (GET yolunu bloklamaz).
        ProfileResponse response = profileService.get(territoryId, principal != null ? principal.id() : null);
        if (principal != null) {
            analyticsService.recordView(territoryId, principal.id());
        }
        return response;
    }

    @Operation(summary = "Alan profilini güncelle (yalnızca sahibi)")
    @PutMapping
    public ProfileResponse update(@PathVariable UUID territoryId,
                                  @AuthenticationPrincipal AuthenticatedUser principal,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(territoryId, principal.id(), request);
    }
}
