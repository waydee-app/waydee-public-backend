package com.waydee.messaging.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.FollowStatus;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.messaging.api.dto.ChatDtos.ConversationResponse;
import com.waydee.messaging.api.dto.ChatDtos.MessageResponse;
import com.waydee.messaging.api.dto.ChatDtos.SendMessageRequest;
import com.waydee.messaging.application.event.MessageSentEvent;
import com.waydee.messaging.domain.Conversation;
import com.waydee.messaging.domain.Message;
import com.waydee.messaging.infrastructure.ConversationRepository;
import com.waydee.messaging.infrastructure.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DM sohbet servisi. Gönderim REST üzerinden yapılır (rate limit + JWT filtresi
 * kapsar), canlı teslim STOMP /user/queue/messages ile olur. Her okuma/yazma
 * katılımcılık doğrulamasından geçer — sohbet id'si tek başına yetki DEĞİLDİR.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int CONVERSATION_PAGE = 50;
    private static final int MAX_MESSAGE_PAGE = 50;
    private static final int PREVIEW_LEN = 140;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;
    private final com.waydee.moderation.application.RestrictionService restrictionService;
    private final com.waydee.identity.infrastructure.FollowRepository followRepository;
    private final com.waydee.identity.application.BlockService blockService;

    @Transactional
    public ConversationResponse getOrCreate(UUID me, UUID recipientId) {
        restrictionService.assertAllowed(me, com.waydee.moderation.domain.RestrictedAction.MESSAGE);
        if (me.equals(recipientId)) {
            throw ApiException.badRequest("Kendinle sohbet başlatamazsın");
        }
        // Engelli taraflar sohbet açamaz (iki yönde de).
        blockService.assertNotBlocked(me, recipientId);
        User other = userRepository.findById(recipientId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        if (!other.isActive()) {
            throw ApiException.notFound("Kullanıcı bulunamadı");
        }
        // ⚠️ PostgreSQL sırası (işaretsiz) — `UUID.compareTo` işaretli karşılaştırır
        // ve çiftin yarısında ters sonuç verir (bkz. UuidOrder).
        UUID lo = com.waydee.messaging.domain.UuidOrder.lo(me, recipientId);
        UUID hi = com.waydee.messaging.domain.UuidOrder.hi(me, recipientId);

        Conversation conversation = conversationRepository.findByUserLoIdAndUserHiId(lo, hi)
                .orElseGet(() -> {
                    try {
                        return conversationRepository.saveAndFlush(new Conversation(me, recipientId));
                    } catch (DataIntegrityViolationException race) {
                        // Aynı anda iki taraf da başlattı — UNIQUE kazandı, mevcut kaydı al.
                        return conversationRepository.findByUserLoIdAndUserHiId(lo, hi)
                                .orElseThrow(() -> ApiException.badRequest("Sohbet oluşturulamadı"));
                    }
                });
        return ConversationResponse.from(conversation, me, UserSummary.from(other));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID me) {
        return summarize(conversationRepository.findMine(me, PageRequest.of(0, CONVERSATION_PAGE)), me);
    }

    /** Mesaj istekleri: takip etmeyen birinin attığı ilk mesajlar burada bekler. */
    @Transactional(readOnly = true)
    public List<ConversationResponse> requests(UUID me) {
        return summarize(conversationRepository.findRequests(me, PageRequest.of(0, CONVERSATION_PAGE)), me);
    }

    @Transactional(readOnly = true)
    public long requestCount(UUID me) {
        return conversationRepository.countRequests(me);
    }

    /** İsteği kabul et → sohbet gelen kutusuna taşınır. */
    @Transactional
    public void acceptRequest(UUID me, UUID conversationId) {
        Conversation conversation = requireParticipant(conversationId, me);
        if (conversation.isRequestFor(me)) {
            conversation.accept();
        }
    }

    /** İsteği reddet → sohbet ve mesajları silinir (karşı taraf tekrar yazabilir). */
    @Transactional
    public void rejectRequest(UUID me, UUID conversationId) {
        Conversation conversation = requireParticipant(conversationId, me);
        if (!conversation.isRequestFor(me)) {
            throw ApiException.badRequest("Bu sohbet bir mesaj isteği değil");
        }
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);
    }

    private List<ConversationResponse> summarize(List<Conversation> conversations, UUID me) {
        if (conversations.isEmpty()) {
            return List.of();
        }
        // Karşı taraf özetleri tek sorguda (N+1 yok).
        List<UUID> otherIds = conversations.stream().map(c -> c.otherOf(me)).distinct().toList();
        Map<UUID, User> users = userRepository.findAllById(otherIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return conversations.stream()
                .map(c -> {
                    User other = users.get(c.otherOf(me));
                    return other == null ? null
                            : ConversationResponse.from(c, me, UserSummary.from(other));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getOne(UUID me, UUID conversationId) {
        Conversation conversation = requireParticipant(conversationId, me);
        User other = userRepository.findById(conversation.otherOf(me))
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        return ConversationResponse.from(conversation, me, UserSummary.from(other));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID me, UUID conversationId, Instant before, int size) {
        requireParticipant(conversationId, me);
        Instant cursor = before != null ? before : Instant.now().plusSeconds(60);
        return messageRepository.pageBefore(conversationId, cursor,
                        PageRequest.of(0, Math.min(Math.max(size, 1), MAX_MESSAGE_PAGE)))
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional
    public MessageResponse send(UUID me, UUID conversationId, SendMessageRequest request) {
        restrictionService.assertAllowed(me, com.waydee.moderation.domain.RestrictedAction.MESSAGE);
        Conversation conversation = requireParticipant(conversationId, me);
        // Sohbet açıldıktan SONRA engellenmiş olabilir — her gönderimde tekrar bakılır.
        blockService.assertNotBlocked(me, conversation.otherOf(me));
        String body = request.body().trim();
        if (body.isEmpty()) {
            throw ApiException.badRequest("Mesaj boş olamaz");
        }

        // İdempotency: aynı clientMessageId ile tekrar gönderim aynı mesajı döndürür.
        if (request.clientMessageId() != null && !request.clientMessageId().isBlank()) {
            Optional<Message> existing = messageRepository
                    .findFirstByConversationIdAndSenderIdAndClientMessageId(
                            conversationId, me, request.clientMessageId());
            if (existing.isPresent()) {
                return MessageResponse.from(existing.get());
            }
        }

        UUID recipientId = conversation.otherOf(me);
        // İlk mesaj: karşı taraf beni takip etmiyorsa sohbet ona "istek" olarak düşer.
        if (conversation.getLastMessageAt() == null) {
            boolean recipientFollowsMe = followRepository
                    .existsByFollowerIdAndFolloweeIdAndStatus(recipientId, me, FollowStatus.ACCEPTED);
            if (!recipientFollowsMe) {
                conversation.markAsRequest(me);
            }
        } else if (conversation.isRequestFor(me)) {
            // İsteği alan taraf cevap yazarsa istek otomatik kabul edilmiş sayılır.
            conversation.accept();
        }

        Message message = messageRepository.saveAndFlush(
                new Message(conversationId, me, body,
                        request.clientMessageId() != null && !request.clientMessageId().isBlank()
                                ? request.clientMessageId() : null));

        String preview = body.length() > PREVIEW_LEN ? body.substring(0, PREVIEW_LEN) : body;
        conversationRepository.touchOnMessage(conversationId, message.getCreatedAt(), preview, recipientId);

        MessageResponse response = MessageResponse.from(message);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MESSAGE");
        payload.put("id", response.id().toString());
        payload.put("conversationId", conversationId.toString());
        payload.put("senderId", me.toString());
        payload.put("body", response.body());
        payload.put("clientMessageId", response.clientMessageId());
        payload.put("createdAt", response.createdAt().toString());
        eventPublisher.publish(new MessageSentEvent(recipientId, me, payload));

        return response;
    }

    @Transactional
    public void markRead(UUID me, UUID conversationId) {
        requireParticipant(conversationId, me);
        conversationRepository.resetUnread(conversationId, me);
    }

    @Transactional(readOnly = true)
    public long unreadTotal(UUID me) {
        return conversationRepository.totalUnread(me);
    }

    private Conversation requireParticipant(UUID conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Sohbet bulunamadı"));
        if (!conversation.isParticipant(userId)) {
            // Varlığı sızdırmamak için 403 değil 404.
            throw ApiException.notFound("Sohbet bulunamadı");
        }
        return conversation;
    }
}
