package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.TagStatsDtos.ImpressionRequest;
import com.waydee.social.api.dto.TagStatsDtos.TagStatsView;
import com.waydee.social.application.PostTagStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Etiket istatistikleri uçları.
 *
 * <p>⚠️ Gösterim bildirimi <b>oturum istemez</b> ama rapor okuma ister:
 * ölçüm ziyaretçiden gelir, rapor yalnız sahibinindir.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostTagStatsController {

    private final PostTagStatsService statsService;

    /**
     * Etiketler ekranda çizildi.
     *
     * <p>⚠️ Yanıt <b>204</b> ve gövdesiz: ölçüm çağrısı arayüzü bekletmemeli,
     * istemci sonucu zaten kullanmıyor.
     */
    @PostMapping("/posts/{postId}/tag-impressions")
    public ResponseEntity<Void> impressions(@PathVariable UUID postId,
                                            @Valid @RequestBody ImpressionRequest request) {
        statsService.recordImpressions(postId, request.tagIds());
        return ResponseEntity.noContent().build();
    }

    /** Gönderinin etiket raporu — yalnız sahibi. */
    @GetMapping("/posts/{postId}/tag-stats")
    public TagStatsView stats(@PathVariable UUID postId,
                              @RequestParam(required = false) Integer days,
                              @AuthenticationPrincipal AuthenticatedUser principal) {
        return statsService.forPost(postId, principal.id(), days);
    }
}
