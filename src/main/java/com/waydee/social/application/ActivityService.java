package com.waydee.social.application;

import com.waydee.common.events.DomainEventPublisher;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.dto.ActivityDtos.ActivityResponse;
import com.waydee.social.application.event.ActivityRecordedEvent;
import com.waydee.social.application.event.PostCreatedEvent;
import com.waydee.social.domain.ActivityEvent;
import com.waydee.social.infrastructure.ActivityEventRepository;
import com.waydee.territory.application.event.TerritoryPurchasedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Son hareketler" akışı: satın alma / paylaşım / etkinlik olaylarını
 * kaynağın transaction'ı İÇİNDE kaydeder (atomik iz) ve commit sonrası
 * WS yayını için ActivityRecordedEvent yayınlar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int MAX_LIMIT = 50;
    /** Panelin isteyebileceği en geniş zaman penceresi (1 gün). */
    private static final int MAX_WINDOW_MINUTES = 24 * 60;

    private final ActivityEventRepository activityRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;

    /** Satın alma → "X bu konumu satın aldı". Kaynak transaction'ında koşar. */
    @EventListener
    @Transactional
    public void onTerritoryPurchased(TerritoryPurchasedEvent event) {
        String territoryName = null;
        if (event.feature() != null && event.feature().get("properties") instanceof Map<?, ?> props) {
            Object name = props.get("name");
            territoryName = name != null ? name.toString() : null;
        }
        record("TERRITORY_PURCHASED", event.buyerId(), event.territoryId(), territoryName);
    }

    /** Gönderi → türüne göre "fotoğraf paylaştı / etkinlik başlattı / anket açtı". */
    @EventListener
    @Transactional
    public void onPostCreated(PostCreatedEvent event) {
        String type = switch (event.kind()) {
            case "EVENT" -> "EVENT_STARTED";
            case "POLL" -> "POLL_CREATED";
            default -> event.hasMedia() ? "PHOTO_SHARED" : "POST_SHARED";
        };
        record(type, event.authorId(), event.territoryId(), event.territoryName());
    }

    /**
     * Son hareketler. {@code withinMinutes > 0} ise yalnız o pencere içindekiler döner
     * (harita paneli 10 dakika kullanır); 0/negatif ise pencere uygulanmaz.
     */
    @Transactional(readOnly = true)
    public List<ActivityResponse> recent(int limit, int withinMinutes) {
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_LIMIT));
        List<ActivityEvent> events = withinMinutes > 0
                ? activityRepository.findByCreatedAtAfterOrderByCreatedAtDesc(
                        Instant.now().minus(Duration.ofMinutes(Math.min(withinMinutes, MAX_WINDOW_MINUTES))), page)
                : activityRepository.findAllByOrderByCreatedAtDesc(page);
        return events.stream().map(ActivityResponse::from).toList();
    }

    private void record(String type, UUID actorId, UUID territoryId, String territoryName) {
        User actor = userRepository.findById(actorId).orElse(null);
        if (actor == null) {
            return;
        }
        ActivityEvent saved = activityRepository.save(new ActivityEvent(
                type, actor.getId(), actor.getUsername(), actor.getDisplayName(),
                actor.getAvatarMediaId(), territoryId, territoryName));
        // Commit sonrası WS yayını — payload hazır, dinleyici DB'ye dokunmaz.
        eventPublisher.publish(new ActivityRecordedEvent(toPayload(ActivityResponse.from(saved))));
    }

    private Map<String, Object> toPayload(ActivityResponse r) {
        java.util.HashMap<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", r.id().toString());
        payload.put("type", r.type());
        payload.put("actorId", r.actorId().toString());
        payload.put("actorUsername", r.actorUsername());
        payload.put("actorDisplayName", r.actorDisplayName());
        payload.put("actorAvatarUrl", r.actorAvatarUrl());
        payload.put("territoryId", r.territoryId() != null ? r.territoryId().toString() : null);
        payload.put("territoryName", r.territoryName());
        payload.put("createdAt", r.createdAt().toString());
        return payload;
    }
}
