package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.StoryDtos;
import com.waydee.social.api.dto.StoryDtos.CreateStoryRequest;
import com.waydee.social.api.dto.StoryDtos.StoryGroupResponse;
import com.waydee.social.api.dto.StoryDtos.StoryResponse;
import com.waydee.social.api.dto.StoryDtos.StoryTargetResponse;
import com.waydee.social.application.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Stories", description = "Instagram tarzı hikayeler (24 saat)")
@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @Operation(summary = "Hikaye paylaş (yüklenen görselden)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoryResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                                @Valid @RequestBody CreateStoryRequest request) {
        return storyService.create(principal.id(), request);
    }

    /**
     * @param scope {@code following} → yalnız takip ettiklerim (ana sayfa şeridi);
     *              boş/diğer → tüm açık hesaplar (keşif şeridi).
     */
    @Operation(summary = "Hikaye şeridi (aktif, yazara göre gruplu)")
    @GetMapping("/feed")
    public List<StoryGroupResponse> feed(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @org.springframework.web.bind.annotation.RequestParam(required = false) String scope) {
        return storyService.feed(principal.id(), "following".equalsIgnoreCase(scope));
    }

    @Operation(summary = "Bir kullanıcının aktif hikayeleri")
    @GetMapping("/user/{userId}")
    public List<StoryResponse> userStories(@PathVariable UUID userId,
                                           @AuthenticationPrincipal AuthenticatedUser principal) {
        return storyService.userStories(userId, principal.id());
    }

    @Operation(summary = "Hikaye paylaşılabilecek bölgelerim (profil türü akış olanlar)")
    @GetMapping("/targets")
    public List<StoryTargetResponse> targets(@AuthenticationPrincipal AuthenticatedUser principal) {
        return storyService.targets(principal.id());
    }

    @Operation(summary = "Bir bölgede yayınlanmış aktif hikayeler")
    @GetMapping("/territory/{territoryId}")
    public List<StoryResponse> territoryStories(@PathVariable UUID territoryId,
                                                @AuthenticationPrincipal AuthenticatedUser principal) {
        return storyService.territoryStories(territoryId, principal.id());
    }

    @Operation(summary = "Hikayeyi görüldü işaretle")
    @PostMapping("/{id}/view")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void view(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        storyService.markViewed(id, principal.id());
    }

    /**
     * 🔒 Yalnız <b>sahibi</b>. Başkasının hikayesini kimin gördüğü, o kişinin
     * çevresini sızdırır; kapı {@code StoryService.viewers} içindedir.
     */
    @Operation(summary = "Hikayeyi kimler gördü (yalnızca sahibi)")
    @GetMapping("/{id}/viewers")
    public List<StoryDtos.StoryViewerResponse> viewers(@PathVariable UUID id,
                                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return storyService.viewers(id, principal.id());
    }

    @Operation(summary = "Hikayeyi sil (yalnızca sahibi)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        storyService.delete(id, principal.id());
    }
}
