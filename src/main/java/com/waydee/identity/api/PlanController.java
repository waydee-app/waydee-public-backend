package com.waydee.identity.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.identity.application.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planım ve <b>bu haftaki kullanımım</b>.
 *
 * <p>Arayüz "bu hafta 1/1 gönderi" gibi bir sayaç ve dolduğunda yükseltme kartı
 * çizebilsin diye tek çağrıda özet döner. (Sınır 10 Ağu 2026'da günlükten
 * <b>haftalığa</b> çekildi.)
 *
 * <p>🔒 Kimlik <b>oturumdan</b> alınır; istekte kullanıcı kimliği kabul edilmez
 * (projenin "kendi giriş geçmişim" ucundaki aynı kural).
 */
@Tag(name = "Plan", description = "Üyelik planı ve kullanım")
@RestController
@RequestMapping("/api/v1/users/me/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "Planım + bu haftaki kullanımım")
    @GetMapping
    public PlanService.PlanUsage myPlan(@AuthenticationPrincipal AuthenticatedUser user) {
        return planService.usage(user.id());
    }
}
