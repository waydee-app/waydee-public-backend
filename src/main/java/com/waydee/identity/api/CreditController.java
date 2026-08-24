package com.waydee.identity.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.identity.application.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * <b>Yapay zekâ kredim</b> (V45).
 *
 * <p>🔒 Kimlik <b>oturumdan</b> alınır; istekte kullanıcı kimliği kabul edilmez
 * — projedeki {@code PlanController} ile aynı kural. Aksi halde herkes
 * başkasının bakiyesini okuyabilirdi.
 */
@Tag(name = "Credits", description = "Yapay zekâ kredisi")
@RestController
@RequestMapping("/api/v1/users/me/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @Operation(summary = "Kredi bakiyem")
    @GetMapping
    public CreditService.CreditSummary balance(@AuthenticationPrincipal AuthenticatedUser user) {
        return creditService.summary(user.id());
    }

    @Operation(summary = "Kredi hareketlerim")
    @GetMapping("/history")
    public List<LedgerItem> history(@AuthenticationPrincipal AuthenticatedUser user,
                                    @RequestParam(defaultValue = "30") int limit) {
        return creditService.history(user.id(), limit).stream()
                .map(e -> new LedgerItem(e.getDelta(), e.getBalanceAfter(),
                        e.getReason().name(), e.getNote(), e.getCreatedAt()))
                .toList();
    }

    /**
     * ⚠️ Defter satırının <b>kimliği ve iş anahtarı DIŞARI VERİLMEZ</b>:
     * {@code ref_key} içinde üretim kimliği geçiyor ve dışarıya sızmasının
     * hiçbir faydası yok. Arayüzün ihtiyacı yalnız "ne oldu, ne kadar, ne zaman".
     */
    public record LedgerItem(int delta, int balanceAfter, String reason, String note, Instant at) {
    }
}
