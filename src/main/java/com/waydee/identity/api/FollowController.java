package com.waydee.identity.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.identity.api.dto.FollowDtos.RelationshipResponse;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.application.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Follows", description = "Takip / arkadaşlık ve istekler")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "Kullanıcıyı takip et (gizli hesapsa istek gönderir)")
    @PostMapping("/users/{userId}/follow")
    public RelationshipResponse follow(@PathVariable UUID userId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.follow(principal.id(), userId);
    }

    @Operation(summary = "Takibi bırak / isteği geri çek")
    @DeleteMapping("/users/{userId}/follow")
    public RelationshipResponse unfollow(@PathVariable UUID userId,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.unfollow(principal.id(), userId);
    }

    @Operation(summary = "Kullanıcıyla ilişkim + sayaçlar")
    @GetMapping("/users/{userId}/relationship")
    public RelationshipResponse relationship(@PathVariable UUID userId,
                                             @AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.relationship(principal.id(), userId);
    }

    @Operation(summary = "Takipçiler")
    @GetMapping("/users/{userId}/followers")
    public List<UserSummary> followers(@PathVariable UUID userId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.followers(userId, principal.id());
    }

    @Operation(summary = "Takip edilenler")
    @GetMapping("/users/{userId}/following")
    public List<UserSummary> following(@PathVariable UUID userId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.following(userId, principal.id());
    }

    @Operation(summary = "Bana gelen takip istekleri")
    @GetMapping("/follow-requests")
    public List<UserSummary> requests(@AuthenticationPrincipal AuthenticatedUser principal) {
        return followService.requests(principal.id());
    }

    @Operation(summary = "İsteği kabul et")
    @PostMapping("/follow-requests/{requesterId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable UUID requesterId,
                       @AuthenticationPrincipal AuthenticatedUser principal) {
        followService.accept(principal.id(), requesterId);
    }

    @Operation(summary = "İsteği reddet")
    @PostMapping("/follow-requests/{requesterId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable UUID requesterId,
                       @AuthenticationPrincipal AuthenticatedUser principal) {
        followService.reject(principal.id(), requesterId);
    }
}
