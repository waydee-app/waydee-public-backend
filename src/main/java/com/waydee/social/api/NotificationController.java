package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.NotificationDtos.NotificationResponse;
import com.waydee.social.api.dto.NotificationDtos.UnreadCountResponse;
import com.waydee.social.application.NotificationService;
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

@Tag(name = "Notifications", description = "Bildirimler (takip + profil görüntüleme)")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Bildirimlerim")
    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return notificationService.list(principal.id());
    }

    @Operation(summary = "Okunmamış bildirim sayısı")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadCountResponse(notificationService.unreadCount(principal.id()));
    }

    @Operation(summary = "Tümünü okundu işaretle")
    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.markAllRead(principal.id());
    }

    /**
     * <b>Tek bildirimi sil</b> (21 Ağu 2026).
     *
     * <p>⚠️ Bulunamayan bildirim de <b>204</b> döner: kullanıcı aynı X'e iki
     * kez basmış olabilir ve bu bir arıza değil, aynı sonucun tekrarıdır.
     * 404 döndürmek, istemciyi anlamsız bir hata mesajı göstermeye zorlardı.
     */
    @Operation(summary = "Bildirimi sil")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        notificationService.delete(principal.id(), id);
    }

    @Operation(summary = "Tüm bildirimleri sil")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        notificationService.deleteAll(principal.id());
    }
}
