package com.waydee.messaging.api.dto;

import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.messaging.domain.Conversation;
import com.waydee.messaging.domain.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateConversationRequest(
            @NotNull(message = "Alıcı zorunludur") UUID recipientId
    ) {
    }

    public record SendMessageRequest(
            @NotBlank(message = "Mesaj boş olamaz")
            @Size(max = 2000, message = "Mesaj en fazla 2000 karakter olabilir")
            String body,

            @Size(max = 64) String clientMessageId
    ) {
    }

    public record ConversationResponse(
            UUID id,
            UserSummary other,
            String lastMessagePreview,
            Instant lastMessageAt,
            int unreadCount,
            /** true → bu sohbet benim için bekleyen bir mesaj isteği. */
            boolean pendingRequest
    ) {
        public static ConversationResponse from(Conversation c, UUID me, UserSummary other) {
            return new ConversationResponse(c.getId(), other, c.getLastMessagePreview(),
                    c.getLastMessageAt(), c.unreadFor(me), c.isRequestFor(me));
        }
    }

    public record MessageResponse(
            UUID id,
            UUID conversationId,
            UUID senderId,
            String body,
            String clientMessageId,
            Instant createdAt
    ) {
        public static MessageResponse from(Message m) {
            return new MessageResponse(m.getId(), m.getConversationId(), m.getSenderId(),
                    m.getBody(), m.getClientMessageId(), m.getCreatedAt());
        }
    }

    public record UnreadCountResponse(long count) {
    }
}
