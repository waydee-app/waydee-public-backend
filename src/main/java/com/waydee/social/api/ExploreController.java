package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.social.api.dto.SocialDtos.PostResponse;
import com.waydee.social.application.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Explore", description = "Keşfet ve ana akış")
/*
 * 🔴 8 Ağu 2026 — KEŞFET KAPALI (kullanıcı isteği).
 * Bayrak `false` iken controller kaydedilmez → `/explore` **404**.
 * ⚠️ Aynı controller'daki `/feed` (takip akışı) da kapanır; istemcide hiçbir
 * yerde çağrılmıyor (ölçüldü), o yüzden ayrıştırmaya gerek görülmedi.
 * Geri açmak: `EXPLORE_ENABLED=true`.
 */
@ConditionalOnProperty(name = "waydee.features.explore.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExploreController {

    private final PostService postService;

    @Operation(summary = "Keşfet akışı (gizli olmayan hesapların gönderileri)")
    @GetMapping("/explore")
    public PageResponse<PostResponse> explore(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "12") int size) {
        return postService.explore(principal.id(), page, size);
    }

    @Operation(summary = "Ana akış — takip ettiklerimin gönderileri")
    @GetMapping("/feed")
    public PageResponse<PostResponse> feed(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return postService.followingFeed(principal.id(), page, size);
    }
}
