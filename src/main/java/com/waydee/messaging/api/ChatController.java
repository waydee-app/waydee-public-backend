package com.waydee.messaging.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.messaging.api.dto.ChatDtos.ConversationResponse;
import com.waydee.messaging.api.dto.ChatDtos.CreateConversationRequest;
import com.waydee.messaging.api.dto.ChatDtos.MessageResponse;
import com.waydee.messaging.api.dto.ChatDtos.SendMessageRequest;
import com.waydee.messaging.api.dto.ChatDtos.UnreadCountResponse;
import com.waydee.messaging.application.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Chat", description = "Kullanıcılar arası DM sohbet")
/*
 * 🔴 8 Ağu 2026 — MESAJLAŞMA KAPALI (kullanıcı isteği).
 * Bayrak `false` iken bu controller Spring'e HİÇ kaydedilmez → tüm
 * `/conversations/**` uçları **404** döner. Kod silinmedi: geri açmak
 * `MESSAGING_ENABLED=true` ile tek satırlık bir iştir.
 * ⚠️ Arayüz tarafı da aynı anda gizlendi; yalnız birini kapatmak ya tıklayınca
 * hata veren bir düğme ya da açıkta kalan bir uç bırakırdı.
 */
@ConditionalOnProperty(name = "waydee.features.messaging.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "Sohbet başlat ya da mevcut olanı getir")
    @PostMapping
    public ResponseEntity<ConversationResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                       @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.getOrCreate(principal.id(), request.recipientId()));
    }

    @Operation(summary = "Sohbetlerim")
    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.list(principal.id());
    }

    @Operation(summary = "Toplam okunmamış mesaj sayısı")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadCountResponse(chatService.unreadTotal(principal.id()));
    }

    @Operation(summary = "Mesaj istekleri (takip etmeyenlerin ilk mesajları)")
    @GetMapping("/requests")
    public List<ConversationResponse> requests(@AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.requests(principal.id());
    }

    @Operation(summary = "Bekleyen mesaj isteği sayısı")
    @GetMapping("/requests/count")
    public UnreadCountResponse requestCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return new UnreadCountResponse(chatService.requestCount(principal.id()));
    }

    @Operation(summary = "Mesaj isteğini kabul et")
    @PostMapping("/{conversationId}/accept")
    public ResponseEntity<Void> accept(@PathVariable UUID conversationId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        chatService.acceptRequest(principal.id(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mesaj isteğini reddet (sohbeti sil)")
    @PostMapping("/{conversationId}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID conversationId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        chatService.rejectRequest(principal.id(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Tek sohbet (başlık bilgisi)")
    @GetMapping("/{conversationId}")
    public ConversationResponse getOne(@PathVariable UUID conversationId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        return chatService.getOne(principal.id(), conversationId);
    }

    @Operation(summary = "Sohbet mesajları (keyset: before imzalı, en yeniden eskiye)")
    @GetMapping("/{conversationId}/messages")
    public List<MessageResponse> messages(@PathVariable UUID conversationId,
                                          @AuthenticationPrincipal AuthenticatedUser principal,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
                                          @RequestParam(defaultValue = "30") int size) {
        return chatService.messages(principal.id(), conversationId, before, size);
    }

    @Operation(summary = "Mesaj gönder")
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<MessageResponse> send(@PathVariable UUID conversationId,
                                                @AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.send(principal.id(), conversationId, request));
    }

    @Operation(summary = "Sohbeti okundu işaretle")
    @PostMapping("/{conversationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID conversationId,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        chatService.markRead(principal.id(), conversationId);
        return ResponseEntity.noContent().build();
    }
}
