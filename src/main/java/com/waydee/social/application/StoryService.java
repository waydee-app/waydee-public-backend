package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.dto.SocialDtos.AuthorSummary;
import com.waydee.social.api.dto.StoryDtos.CreateStoryRequest;
import com.waydee.social.api.dto.StoryDtos.StoryGroupResponse;
import com.waydee.social.api.dto.StoryDtos.StoryResponse;
import com.waydee.social.api.dto.StoryDtos.StoryTargetResponse;
import com.waydee.social.domain.ProfileType;
import com.waydee.social.domain.Story;
import com.waydee.social.domain.StoryView;
import com.waydee.social.infrastructure.StoryRepository;
import com.waydee.social.infrastructure.StoryViewRepository;
import com.waydee.social.infrastructure.TerritoryProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hikaye (story) yönetimi: oluşturma, feed (yazara göre gruplu), kullanıcı hikayeleri,
 * görüldü işaretleme ve silme. Hikayeler 24 saat sonra "kaybolur" (expiresAt filtresi).
 */
@Service
@RequiredArgsConstructor
public class StoryService {

    private static final Duration TTL = Duration.ofHours(24);

    private static final int FEED_LIMIT = 300;

    /** Görüntüleyen listesinin tavanı — bkz. {@link #viewers}. */
    private static final int VIEWER_LIMIT = 200;

    private final StoryRepository storyRepository;
    private final StoryViewRepository viewRepository;
    private final MediaService mediaService;
    private final UserRepository userRepository;
    private final ContentAccessService contentAccessService;
    private final com.waydee.moderation.application.RestrictionService restrictionService;
    private final com.waydee.territory.infrastructure.TerritoryRepository territoryRepository;
    private final TerritoryProfileRepository profileRepository;

    /**
     * Hikaye paylaşır.
     *
     * **Çoklu bölge:** seçilen her bölge için ayrı bir `Story` satırı yazılır (aynı
     * medya, aynı açıklama). Böylece görüldü/silme her bölgede bağımsız çalışır ve
     * bölge şeridi sorgusu (`territory_id = ?`) basit kalır. Hiç bölge seçilmezse
     * tek bir "yalnız profilimde" hikayesi oluşur.
     *
     * Dönen yanıt ilk hikayedir (istemci zaten listeleri tazeler).
     */
    @Transactional
    public StoryResponse create(UUID authorId, CreateStoryRequest request) {
        restrictionService.assertAllowed(authorId, com.waydee.moderation.domain.RestrictedAction.STORY);
        mediaService.assertOwnedBy(request.mediaId(), authorId);
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        String caption = request.caption() != null && !request.caption().isBlank()
                ? request.caption().trim() : null;

        // Eski tek alanlı istemciler + yeni çoklu alan birleştirilir, tekilleştirilir.
        List<UUID> requested = new ArrayList<>();
        if (request.territoryId() != null) {
            requested.add(request.territoryId());
        }
        if (request.territoryIds() != null) {
            request.territoryIds().stream().filter(java.util.Objects::nonNull).forEach(requested::add);
        }
        List<UUID> targets = requested.stream().distinct().map(id -> validateTarget(id, authorId)).toList();

        List<Story> created = new ArrayList<>();
        if (targets.isEmpty()) {
            created.add(storyRepository.save(new Story(author, request.mediaId(), caption, null, TTL)));
        } else {
            for (UUID territoryId : targets) {
                created.add(storyRepository.save(new Story(author, request.mediaId(), caption, territoryId, TTL)));
            }
        }
        // yeni hikaye: sahibi görmüş sayılır; bölgeye atıldıysa adı da dönsün
        return toResponse(created.get(0), true, false, territoryNames(created));
    }

    /**
     * Hikayenin yayınlanacağı bölgeyi doğrular: sahibi olmalı, aktif/görünür olmalı ve
     * profil türü **STANDARD (akış)** olmalı — gömülü site/HTML profilinde akış yoktur,
     * oraya hikaye düşerse hiçbir yerde görünmez.
     */
    private UUID validateTarget(UUID territoryId, UUID authorId) {
        if (territoryId == null) {
            return null;
        }
        com.waydee.territory.domain.Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Bölge bulunamadı"));
        if (!territory.getOwner().getId().equals(authorId)) {
            throw ApiException.forbidden("Bu bölgenin sahibi değilsiniz");
        }
        if (territory.getStatus() != com.waydee.territory.domain.TerritoryStatus.ACTIVE || territory.isHidden()) {
            throw ApiException.badRequest("Bu bölge şu anda yayında değil");
        }
        boolean feedProfile = profileRepository.findById(territoryId)
                .map(p -> p.getProfileType() == null || p.getProfileType() == ProfileType.STANDARD)
                .orElse(true);
        if (!feedProfile) {
            throw ApiException.badRequest("Bu bölgenin profil türü akış değil; hikaye paylaşılamaz");
        }
        return territoryId;
    }

    /**
     * Story paylaşırken seçilebilecek bölgeler: kullanıcının aktif, görünür ve
     * profil türü akış olan bölgeleri. Boş liste = yalnız kendi profiline paylaşabilir.
     */
    @Transactional(readOnly = true)
    public List<StoryTargetResponse> targets(UUID userId) {
        List<com.waydee.territory.domain.Territory> owned =
                territoryRepository.findByOwnerIdOrderByPurchasedAtDesc(userId);
        if (owned.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = owned.stream().map(com.waydee.territory.domain.Territory::getId).toList();
        // Profil türü akış OLMAYAN bölgeler elenir (tek sorguda).
        Set<UUID> nonFeed = profileRepository.findAllById(ids).stream()
                .filter(p -> p.getProfileType() != null && p.getProfileType() != ProfileType.STANDARD)
                .map(com.waydee.social.domain.TerritoryProfile::getTerritoryId)
                .collect(Collectors.toSet());
        return owned.stream()
                .filter(t -> t.getStatus() == com.waydee.territory.domain.TerritoryStatus.ACTIVE && !t.isHidden())
                .filter(t -> !nonFeed.contains(t.getId()))
                .map(t -> new StoryTargetResponse(t.getId(), t.getName(), null))
                .toList();
    }

    /** Bir bölgede yayınlanmış aktif hikayeler (bölge profilindeki şerit). */
    @Transactional(readOnly = true)
    public List<StoryResponse> territoryStories(UUID territoryId, UUID viewerId) {
        com.waydee.territory.domain.Territory territory = territoryRepository.findWithOwnerById(territoryId)
                .orElseThrow(() -> ApiException.notFound("Bölge bulunamadı"));
        // Gizli hesabın bölge içeriği yalnız sahibine ve takipçilerine açık.
        contentAccessService.assertCanView(viewerId, territory.getOwner());
        List<Story> active = storyRepository.findActiveByTerritory(territoryId, Instant.now());
        if (active.isEmpty()) {
            return List.of();
        }
        boolean mine = territory.getOwner().getId().equals(viewerId);
        Set<UUID> seen = viewedIds(viewerId, active);
        Map<UUID, String> names = Map.of(territoryId, territory.getName());
        return active.stream()
                .map(s -> toResponse(s, mine || seen.contains(s.getId()), mine, names))
                .toList();
    }

    /**
     * Görüntüleyene görünür aktif hikayeler, yazara göre gruplu (story şeridi).
     * Gizli hesapların hikayeleri yalnız takipçilerine düşer; sorgu sınırlı.
     */
    @Transactional(readOnly = true)
    public List<StoryGroupResponse> feed(UUID viewerId) {
        return feed(viewerId, false);
    }

    /**
     * @param followingOnly {@code true} ise yalnız <b>takip ettiklerimin</b> (ve
     *                      kendi) hikayelerim döner — ana sayfadaki şerit budur.
     *                      {@code false} keşif şeridi: tüm açık hesaplar.
     */
    @Transactional(readOnly = true)
    public List<StoryGroupResponse> feed(UUID viewerId, boolean followingOnly) {
        var page = org.springframework.data.domain.PageRequest.of(0, FEED_LIMIT);
        List<Story> active = followingOnly
                ? storyRepository.findActiveFollowing(Instant.now(), viewerId, page)
                : storyRepository.findActiveVisible(Instant.now(), viewerId, page);
        if (active.isEmpty()) {
            return List.of();
        }
        Set<UUID> seen = viewedIds(viewerId, active);
        Map<UUID, String> names = territoryNames(active);

        Map<UUID, List<Story>> byAuthor = active.stream()
                .collect(Collectors.groupingBy(s -> s.getAuthor().getId(), LinkedHashMap::new, Collectors.toList()));

        List<StoryGroupResponse> groups = new ArrayList<>();
        for (List<Story> stories : byAuthor.values()) {
            User author = stories.get(0).getAuthor();
            boolean mine = author.getId().equals(viewerId);
            List<StoryResponse> items = stories.stream()
                    .map(s -> toResponse(s, mine || seen.contains(s.getId()), mine, names))
                    .toList();
            boolean hasUnseen = items.stream().anyMatch(s -> !s.seen());
            groups.add(new StoryGroupResponse(AuthorSummary.from(author), items, hasUnseen));
        }
        // Görülmemişi olanlar üstte; sonra en yeni hikayeye göre.
        groups.sort(Comparator
                .comparing(StoryGroupResponse::hasUnseen).reversed()
                .thenComparing(g -> g.stories().get(g.stories().size() - 1).createdAt(), Comparator.reverseOrder()));
        return groups;
    }

    /** Bir kullanıcının aktif hikayeleri (profil halkasına dokununca). Gizli hesap gate'li. */
    @Transactional(readOnly = true)
    public List<StoryResponse> userStories(UUID authorId, UUID viewerId) {
        contentAccessService.assertCanViewByOwnerId(viewerId, authorId);
        List<Story> active = storyRepository.findActiveByAuthor(authorId, Instant.now());
        if (active.isEmpty()) {
            return List.of();
        }
        boolean mine = authorId.equals(viewerId);
        Set<UUID> seen = viewedIds(viewerId, active);
        Map<UUID, String> names = territoryNames(active);
        return active.stream()
                .map(s -> toResponse(s, mine || seen.contains(s.getId()), mine, names))
                .toList();
    }

    @Transactional
    public void markViewed(UUID storyId, UUID viewerId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ApiException.notFound("Hikaye bulunamadı"));
        if (story.getAuthor().getId().equals(viewerId)) {
            return; // kendi hikayeni görmen sayılmaz
        }
        if (!viewRepository.existsByStoryIdAndViewerId(storyId, viewerId)) {
            viewRepository.save(new StoryView(storyId, viewerId));
        }
    }

    /**
     * <b>Hikayeyi kimler gördü</b> — göz simgesine dokununca açılan liste.
     *
     * <p>🔴 16 Ağu 2026, kullanıcı isteği: <i>"göz simgesi kaç kişinin
     * baktığını gösteriyor, ona tıklayınca da kimlerin baktığını göreceğimiz
     * geliştirmeyi yap."</i>
     *
     * <p>🔒 <b>Kapı sunucuda ve serttir: yalnız SAHİBİ.</b> Başkasının
     * hikayesini kimin gördüğü, o kişinin çevresini (kimlerin takip ettiğini,
     * kimlerin aktif olduğunu) dolaylı olarak sızdırır. Arayüzün düğmeyi
     * gizlemesi güvenlik sayılmaz (vault kuralı) — bu yüzden kontrol burada,
     * istemcide değil.
     *
     * <p>⚠️ 403 değil <b>404 → 403</b> sırası: önce hikaye var mı bakılır,
     * sonra sahiplik. Ters sırada, olmayan bir hikaye için "yetkiniz yok"
     * denir ve hikayenin varlığı hakkında yanlış bilgi verilirdi.
     *
     * <p>⚠️ Tavan {@code VIEWER_LIMIT}: liste bir hikaye penceresinde
     * kaydırılarak okunur, sınırsız değildir. Popüler bir hesapta binlerce
     * satırı tek yanıtta taşımak hem sunucuyu hem telefonu yorardı.
     */
    @Transactional(readOnly = true)
    public List<com.waydee.social.api.dto.StoryDtos.StoryViewerResponse> viewers(UUID storyId, UUID requesterId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ApiException.notFound("Hikaye bulunamadı"));
        if (!story.getAuthor().getId().equals(requesterId)) {
            throw ApiException.forbidden("Bu hikayenin görüntüleyenlerini yalnız sahibi görebilir");
        }
        var page = org.springframework.data.domain.PageRequest.of(0, VIEWER_LIMIT);
        return viewRepository.findViewers(storyId, page).stream()
                .map(r -> new com.waydee.social.api.dto.StoryDtos.StoryViewerResponse(
                        new AuthorSummary(r.getId(), r.getUsername(), r.getDisplayName(),
                                r.getAvatarMediaId() == null ? null : MediaUrls.of(r.getAvatarMediaId())),
                        r.getViewedAt()))
                .toList();
    }

    @Transactional
    public void delete(UUID storyId, UUID userId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> ApiException.notFound("Hikaye bulunamadı"));
        if (!story.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("Bu hikayeyi silme yetkiniz yok");
        }
        storyRepository.delete(story);
    }

    private Set<UUID> viewedIds(UUID viewerId, List<Story> stories) {
        if (viewerId == null) {
            return Set.of();
        }
        List<UUID> ids = stories.stream().map(Story::getId).toList();
        return Set.copyOf(viewRepository.findViewedStoryIds(viewerId, ids));
    }

    /**
     * Hikaye listesindeki bölgelerin adları — **tek sorguda** (aksi hâlde her
     * hikaye için ayrı bölge sorgusu = N+1).
     */
    private Map<UUID, String> territoryNames(List<Story> stories) {
        List<UUID> ids = stories.stream()
                .map(Story::getTerritoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return territoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(com.waydee.territory.domain.Territory::getId,
                        com.waydee.territory.domain.Territory::getName, (a, b) -> a));
    }

    private StoryResponse toResponse(Story story, boolean seen, boolean mine) {
        return toResponse(story, seen, mine, Map.of());
    }

    private StoryResponse toResponse(Story story, boolean seen, boolean mine, Map<UUID, String> territoryNames) {
        long viewCount = mine ? viewRepository.countByStoryId(story.getId()) : 0;
        return new StoryResponse(
                story.getId(),
                MediaUrls.of(story.getMediaId()),
                story.getCaption(),
                story.getCreatedAt(),
                story.getExpiresAt(),
                seen,
                mine,
                viewCount,
                story.getTerritoryId(),
                story.getTerritoryId() == null ? null : territoryNames.get(story.getTerritoryId()));
    }
}
