package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.application.event.PostCreatedEvent;
import com.waydee.territory.domain.Territory;
import com.waydee.social.api.dto.SocialDtos.AuthorSummary;
import com.waydee.social.api.dto.SocialDtos.CommentResponse;
import com.waydee.social.api.dto.SocialDtos.CreateCommentRequest;
import com.waydee.social.api.dto.SocialDtos.CreatePostRequest;
import com.waydee.social.api.dto.SocialDtos.EventView;
import com.waydee.social.api.dto.SocialDtos.PollOptionView;
import com.waydee.social.api.dto.SocialDtos.PollView;
import com.waydee.social.api.dto.SocialDtos.PostResponse;
import com.waydee.social.domain.EventRsvp;
import com.waydee.social.domain.MediaObject;
import com.waydee.social.domain.PollOption;
import com.waydee.social.domain.PollVote;
import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostComment;
import com.waydee.social.domain.PostKind;
import com.waydee.social.domain.PostLike;
import com.waydee.social.domain.RsvpStatus;
import com.waydee.social.infrastructure.EventRsvpRepository;
import com.waydee.social.infrastructure.MediaObjectRepository;
import com.waydee.social.infrastructure.PollOptionRepository;
import com.waydee.social.infrastructure.PollVoteRepository;
import com.waydee.social.infrastructure.PostCommentRepository;
import com.waydee.social.infrastructure.PostLikeRepository;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.TerritoryProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final com.waydee.social.infrastructure.PostSaveRepository saveRepository;
    private final NotificationService notificationService;
    private final PostCommentRepository commentRepository;
    private final MediaObjectRepository mediaRepository;
    private final TerritoryProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final TerritoryProfileService profileService;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final EventRsvpRepository eventRsvpRepository;
    private final ContentAccessService contentAccessService;
    private final DomainEventPublisher eventPublisher;
    private final com.waydee.moderation.application.RestrictionService restrictionService;

    @Transactional
    public PostResponse create(UUID territoryId, UUID authorId, CreatePostRequest request) {
        restrictionService.assertAllowed(authorId, com.waydee.moderation.domain.RestrictedAction.POST);
        Territory territory = profileService.requireTerritory(territoryId);
        if (!territory.getOwner().getId().equals(authorId)) {
            throw ApiException.forbidden("Bu alanın sahibi değilsiniz");
        }
        PostKind kind = parseKind(request.kind());
        boolean hasCaption = request.caption() != null && !request.caption().isBlank();
        boolean hasMedia = request.mediaIds() != null && !request.mediaIds().isEmpty();

        User author = userRepository.findById(authorId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        Post post = new Post(territoryId, author, hasCaption ? request.caption().trim() : null);

        switch (kind) {
            case STANDARD -> {
                if (!hasCaption && !hasMedia) {
                    throw ApiException.badRequest("Gönderi boş olamaz: açıklama ya da görsel ekleyin");
                }
                attachMedia(post, request, authorId);
            }
            case POLL -> {
                if (!hasCaption) {
                    throw ApiException.badRequest("Anket sorusu gerekli");
                }
                List<String> options = request.pollOptions() == null ? List.of() : request.pollOptions().stream()
                        .filter(o -> o != null && !o.isBlank())
                        .map(String::trim)
                        .toList();
                if (options.size() < 2) {
                    throw ApiException.badRequest("Ankette en az 2 seçenek olmalı");
                }
                int i = 0;
                for (String option : options) {
                    post.addPollOption(option, i++);
                }
            }
            case EVENT -> {
                if (request.eventTitle() == null || request.eventTitle().isBlank()) {
                    throw ApiException.badRequest("Etkinlik başlığı gerekli");
                }
                if (request.eventStartsAt() == null) {
                    throw ApiException.badRequest("Etkinlik tarihi gerekli");
                }
                String location = request.eventLocation() != null && !request.eventLocation().isBlank()
                        ? request.eventLocation().trim() : null;
                post.makeEvent(request.eventTitle().trim(), location, request.eventStartsAt());
                attachMedia(post, request, authorId);
            }
        }

        postRepository.save(post);
        profileRepository.adjustPostCount(territoryId, 1);
        // Aktivite akışı beslemesi ("X fotoğraf paylaştı / etkinlik başlattı").
        eventPublisher.publish(new PostCreatedEvent(post.getId(), territoryId, territory.getName(),
                authorId, kind.name(), hasMedia));
        return toResponse(post, false, false, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> list(UUID territoryId, UUID viewerId, int page, int size) {
        // Gizli hesap içeriği yalnız sahibine ve kabul edilmiş takipçilerine açık.
        Territory territory = profileService.requireTerritory(territoryId);
        contentAccessService.assertCanView(viewerId, territory.getOwner());

        // İki adımlı sayfalama: önce yalnız id sayfası (SQL LIMIT), sonra detay fetch.
        // Tek adımda collection fetch + Pageable, tüm satırları belleğe çekerdi (HHH000104).
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<UUID> idPage = postRepository.pageIdsByTerritory(territoryId, pageable);
        List<PostResponse> content = enrich(loadOrdered(idPage.getContent()), viewerId);
        return new PageResponse<>(content, idPage.getNumber(), idPage.getSize(),
                idPage.getTotalElements(), idPage.getTotalPages(), idPage.hasNext());
    }

    /**
     * Gönderi gerçekten bu kullanıcıya mı ait?
     *
     * <p>Silinmiş gönderi <b>yok sayılır</b> (404): silinmiş bir içeriğin
     * sahipliğini onaylamak, onu yeniden kullanılabilir kılardı.
     *
     * <p>⚠️ Bu kontrol {@code byAuthor} süzgeciyle KARIŞTIRILMAMALI: orada
     * liste zaten bir yazara göre süzülüdür, burada ise istemcinin gönderdiği
     * <b>serbest bir kimlik</b> doğrulanır (mağaza rafına ürün eklerken
     * başkasının fotoğrafının konmasını engelleyen kapı budur).
     */
    @Transactional(readOnly = true)
    public void assertAuthoredBy(UUID postId, UUID userId) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
        if (!post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("Bu gönderi size ait değil");
        }
    }

    /**
     * Birden çok gönderinin <b>ilk görsel adresi</b> — kimlikten adrese eşleme.
     *
     * <p>3B mağaza rafı bunu kullanır: raftaki ürünler gönderilere bağlıdır ve
     * görsel <b>kopyalanmaz</b> (kullanıcı fotoğrafı değiştirince raf da
     * güncellensin). Tek çağrıda çözülür — ürün başına sorgu, 40 dükkânlık bir
     * caddede yüzlerce gidiş-dönüş demekti.
     *
     * <p>⚠️ Silinmiş gönderi ve görselsiz gönderi haritada <b>hiç yer almaz</b>;
     * çağıran taraf {@code null} görüp o ürünü çizmemeli.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> firstImageUrls(Collection<UUID> postIds, UUID viewerId) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new java.util.HashMap<>();
        for (PostResponse post : enrich(postRepository.findAllWithDetailsByIdIn(postIds), viewerId)) {
            if (post.mediaUrls() != null && !post.mediaUrls().isEmpty()) {
                out.put(post.id(), post.mediaUrls().getFirst());
            }
        }
        return out;
    }

    /** Bir kullanıcının gönderileri (profil ızgarası). Gizli hesapta takipçi gate'i uygulanır. */
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> byAuthor(UUID authorId, UUID viewerId, int page, int size) {
        contentAccessService.assertCanViewByOwnerId(viewerId, authorId);
        Pageable pageable = PageRequest.of(page, Math.min(size, 30));
        Page<UUID> idPage = postRepository.pageIdsByAuthor(authorId, pageable);
        List<PostResponse> content = enrich(loadOrdered(idPage.getContent()), viewerId);
        return new PageResponse<>(content, idPage.getNumber(), idPage.getSize(),
                idPage.getTotalElements(), idPage.getTotalPages(), idPage.hasNext());
    }

    /** Ana akış (home): takip ettiklerimin ve kendi gönderilerim. */
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> followingFeed(UUID viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 30));
        Page<UUID> idPage = postRepository.pageFollowingIds(viewerId, pageable);
        List<PostResponse> content = enrich(loadOrdered(idPage.getContent()), viewerId);
        return new PageResponse<>(content, idPage.getNumber(), idPage.getSize(),
                idPage.getTotalElements(), idPage.getTotalPages(), idPage.hasNext());
    }

    /** Keşfet: gizli olmayan, aktif hesapların gönderileri (kendi gönderilerin hariç). */
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> explore(UUID viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 30));
        Page<UUID> idPage = postRepository.pageExploreIds(viewerId, pageable);
        List<PostResponse> content = enrich(loadOrdered(idPage.getContent()), viewerId);
        return new PageResponse<>(content, idPage.getNumber(), idPage.getSize(),
                idPage.getTotalElements(), idPage.getTotalPages(), idPage.hasNext());
    }

    /** Id sırasını koruyarak detaylı (author+media) yükler. */
    private List<Post> loadOrdered(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Post> byId = postRepository.findAllWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Görüntüleyen bağlamını (beğeni/oy/rsvp) TEK toplu sorguyla ekler — N+1 yok. */
    private List<PostResponse> enrich(List<Post> posts, UUID viewerId) {
        List<UUID> ids = posts.stream().map(Post::getId).toList();
        Set<UUID> likedIds = viewerId != null && !ids.isEmpty()
                ? new HashSet<>(likeRepository.findLikedPostIds(viewerId, ids))
                : Set.of();
        // UYARI: kaydetme de TOPLU sorulur - karo basina bir istek N+1 demekti.
        Set<UUID> savedIds = viewerId != null && !ids.isEmpty()
                ? new HashSet<>(saveRepository.findSavedPostIds(viewerId, ids))
                : Set.of();

        Map<UUID, UUID> myVotes = new HashMap<>();
        Map<UUID, String> myRsvps = new HashMap<>();
        if (viewerId != null && !ids.isEmpty()) {
            for (PollVote vote : pollVoteRepository.findByPostIdInAndUserId(ids, viewerId)) {
                myVotes.put(vote.getPostId(), vote.getOptionId());
            }
            for (EventRsvp rsvp : eventRsvpRepository.findByPostIdInAndUserId(ids, viewerId)) {
                myRsvps.put(rsvp.getPostId(), rsvp.getStatus().name());
            }
        }
        return posts.stream()
                .map(post -> toResponse(post, likedIds.contains(post.getId()),
                        savedIds.contains(post.getId()),
                        myVotes.get(post.getId()), myRsvps.get(post.getId())))
                .toList();
    }

    @Transactional
    public void like(UUID postId, UUID userId) {
        restrictionService.assertAllowed(userId, com.waydee.moderation.domain.RestrictedAction.REACT);
        Post post = requireActivePost(postId);
        PostLike.PostLikeId id = new PostLike.PostLikeId(post.getId(), userId);
        if (likeRepository.existsById(id)) {
            return;
        }
        likeRepository.save(new PostLike(post.getId(), userId));
        postRepository.adjustLikeCount(post.getId(), 1);
        // Sahibine haber ver (V39). Kendi gonderini begenmek bildirim uretmez.
        notificationService.notifyPost(post.getAuthor().getId(),
                com.waydee.social.domain.NotificationType.POST_LIKE, userId, post.getId());
    }

    @Transactional
    public void unlike(UUID postId, UUID userId) {
        PostLike.PostLikeId id = new PostLike.PostLikeId(postId, userId);
        if (!likeRepository.existsById(id)) {
            return;
        }
        likeRepository.deleteById(id);
        postRepository.adjustLikeCount(postId, -1);
    }

    /**
     * <b>Gonderiyi kaydet</b> (yer imi).
     *
     * <p>Tablo V21'den beri vardi ama hicbir kod ona dokunmuyordu; kaydetme
     * yalniz bolgeler icin yazilmisti.
     *
     * <p>UYARI: begeni gibi <b>idempotent</b> - ikinci cagri hicbir sey yapmaz.
     * Aksi halde cift dokunus sayaci ikiye cikarirdi.
     *
     * <p>UYARI: kisitlama kapisi begeniyle AYNI ({@code REACT}); kaydetmek de
     * bir etkilesimdir ve kisitli hesap onu da yapamamali.
     *
     * <p>UYARI: bildirim <b>gonderilmez</b>. Kaydetme sessiz bir eylemdir;
     * sahibine haber verilseydi kullanicilar kaydetmekten cekinirdi.
     */
    @Transactional
    public void save(UUID postId, UUID userId) {
        restrictionService.assertAllowed(userId, com.waydee.moderation.domain.RestrictedAction.REACT);
        Post post = requireActivePost(postId);
        var id = new com.waydee.social.domain.PostSave.PostSaveId(post.getId(), userId);
        if (saveRepository.existsById(id)) {
            return;
        }
        saveRepository.save(new com.waydee.social.domain.PostSave(post.getId(), userId));
        postRepository.adjustSaveCount(post.getId(), 1);
        /* V39: kaydetme ARTIK BILDIRILIYOR. V38'de bilincli olarak sessizdi;
           kullanici tersini istedi - bu uruncte kaydetme satin alma niyetine
           en yakin sinyal ve sahibinin gormesi isteniyor. */
        notificationService.notifyPost(post.getAuthor().getId(),
                com.waydee.social.domain.NotificationType.POST_SAVE, userId, post.getId());
    }

    @Transactional
    public void unsave(UUID postId, UUID userId) {
        var id = new com.waydee.social.domain.PostSave.PostSaveId(postId, userId);
        if (!saveRepository.existsById(id)) {
            return;
        }
        saveRepository.deleteById(id);
        postRepository.adjustSaveCount(postId, -1);
    }

    /**
     * <b>Tek gonderi</b> - detay ekrani ve dogrudan baglantilar icin.
     *
     * <p>GUVENLIK: gizlilik kapisi burada da isler. Gizli hesabin gonderisi,
     * baglantiyi bilen herkese acilamaz; akista suzuluyor olmasi yeterli
     * degildi, dogrudan adres yazan biri suzgeci atlardi.
     */
    @Transactional(readOnly = true)
    public PostResponse one(UUID postId, UUID viewerId) {
        Post post = requireActivePost(postId);
        contentAccessService.assertCanView(viewerId, post.getAuthor());
        return enrich(List.of(post), viewerId).get(0);
    }

    /** Begendiklerim - begenme anina gore sirali. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PostResponse> likedPosts(
            UUID userId, org.springframework.data.domain.Pageable pageable) {
        return likeRepository.findLikedPosts(userId, pageable)
                .map(p -> enrich(List.of(p), userId).get(0));
    }

    /** Kaydettiklerim - kaydetme anina gore sirali. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PostResponse> savedPosts(
            UUID userId, org.springframework.data.domain.Pageable pageable) {
        return saveRepository.findSavedPosts(userId, pageable)
                .map(p -> enrich(List.of(p), userId).get(0));
    }

    /** Ankete oy ver (anket başına tek oy, değiştirilemez). */
    @Transactional
    public PostResponse vote(UUID postId, UUID optionId, UUID userId) {
        restrictionService.assertAllowed(userId, com.waydee.moderation.domain.RestrictedAction.REACT);
        Post post = requireActivePost(postId);
        if (post.getKind() != PostKind.POLL) {
            throw ApiException.badRequest("Bu gönderi bir anket değil");
        }
        PollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> ApiException.notFound("Seçenek bulunamadı"));
        if (!option.getPost().getId().equals(postId)) {
            throw ApiException.badRequest("Seçenek bu ankete ait değil");
        }
        if (pollVoteRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            throw ApiException.badRequest("Bu ankete zaten oy verdiniz");
        }
        pollVoteRepository.save(new PollVote(postId, optionId, userId));
        // Atomik artış (x = x + 1): eşzamanlı oylarda kayıp güncelleme olmaz.
        // clearAutomatically sonrası taze fetch → yanıt da DB ile birebir güncel.
        pollOptionRepository.adjustVoteCount(optionId, 1);
        Post fresh = requireActivePost(postId);
        return toResponse(fresh, likedByMe(postId, userId), savedByMe(postId, userId), optionId, null);
    }

    /** Etkinliğe katılım (GOING/INTERESTED). Aynı durumu tekrar seçmek geri alır (toggle). */
    @Transactional
    public PostResponse rsvp(UUID postId, UUID userId, String statusValue) {
        restrictionService.assertAllowed(userId, com.waydee.moderation.domain.RestrictedAction.REACT);
        Post post = requireActivePost(postId);
        if (post.getKind() != PostKind.EVENT) {
            throw ApiException.badRequest("Bu gönderi bir etkinlik değil");
        }
        RsvpStatus status = parseRsvp(statusValue);
        Optional<EventRsvp> existing = eventRsvpRepository.findByPostIdAndUserId(postId, userId);
        String myRsvp;
        // Sayaçlar atomik UPDATE ile artar (x = x + delta): versiyon çakışması (409) ve
        // kayıp güncelleme olmaz. clearAutomatically sonrası taze fetch ile yanıt günceldir.
        if (existing.isEmpty()) {
            eventRsvpRepository.save(new EventRsvp(postId, userId, status));
            adjustRsvpCounter(postId, status, 1);
            myRsvp = status.name();
        } else {
            EventRsvp rsvp = existing.get();
            if (rsvp.getStatus() == status) {
                eventRsvpRepository.delete(rsvp);
                adjustRsvpCounter(postId, status, -1);
                myRsvp = null;
            } else {
                RsvpStatus previous = rsvp.getStatus();
                rsvp.setStatus(status);
                adjustRsvpCounter(postId, previous, -1);
                adjustRsvpCounter(postId, status, 1);
                myRsvp = status.name();
            }
        }
        Post fresh = requireActivePost(postId);
        return toResponse(fresh, likedByMe(postId, userId), savedByMe(postId, userId), null, myRsvp);
    }

    private void adjustRsvpCounter(UUID postId, RsvpStatus status, int delta) {
        if (status == RsvpStatus.GOING) {
            postRepository.adjustGoingCount(postId, delta);
        } else {
            postRepository.adjustInterestedCount(postId, delta);
        }
    }

    @Transactional
    public CommentResponse comment(UUID postId, UUID authorId, CreateCommentRequest request) {
        restrictionService.assertAllowed(authorId, com.waydee.moderation.domain.RestrictedAction.COMMENT);
        Post post = requireActivePost(postId);
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        PostComment comment = commentRepository.save(
                new PostComment(post.getId(), author, request.body().trim()));
        postRepository.adjustCommentCount(post.getId(), 1);
        // Yeni yorumu yazan onu silebilir — istemci düğmeyi hemen gösterebilsin.
        return CommentResponse.from(comment, authorId, post.getAuthor().getId(), false);
    }

    /**
     * Yorum siler — <b>iki taraf da yetkilidir</b>.
     *
     * <p>⚠️ Kural bilinçli: yorumu <b>yazan</b> kişi kendi sözünü geri
     * alabilmeli, gönderinin <b>sahibi</b> de kendi gönderisinin altındaki
     * bir yorumu kaldırabilmeli (kendi alanında istemediği bir şeyi taşımak
     * zorunda değil). Yönetici her ikisini de kapsar.
     *
     * <p>Silme <b>soft-delete</b>: kayıt durur, listeden düşer — moderasyon
     * ve şikayet incelemesi için iz gerekiyor.
     */
    @Transactional
    public void deleteComment(UUID commentId, UUID userId, boolean isAdmin) {
        PostComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Yorum bulunamadı"));

        Post post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));

        boolean isCommentAuthor = comment.getAuthor().getId().equals(userId);
        boolean isPostOwner = post.getAuthor().getId().equals(userId);
        if (!isAdmin && !isCommentAuthor && !isPostOwner) {
            throw ApiException.forbidden("Bu yorumu silme yetkiniz yok");
        }

        comment.softDelete();
        // Sayaç atomik düşer — eşzamanlı silmede eksiye inmez (repository koşullu).
        postRepository.adjustCommentCount(post.getId(), -1);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> comments(UUID postId, UUID viewerId, boolean isAdmin, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        // Gönderi sahibi bir kez okunur; her yorum için sorgu atmak N+1 olurdu.
        UUID postOwnerId = postRepository.findById(postId)
                .map(p -> p.getAuthor().getId())
                .orElse(null);
        return PageResponse.from(
                commentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId, pageable),
                comment -> CommentResponse.from(comment, viewerId, postOwnerId, isAdmin));
    }

    @Transactional
    public void delete(UUID postId, UUID userId, boolean isAdmin) {
        Post post = requireActivePost(postId);
        if (!isAdmin && !post.getAuthor().getId().equals(userId)) {
            throw ApiException.forbidden("Bu gönderiyi silme yetkiniz yok");
        }
        post.softDelete();
        profileRepository.adjustPostCount(post.getTerritoryId(), -1);
    }

    // ------------------------------------------------------------ helpers

    private void attachMedia(Post post, CreatePostRequest request, UUID authorId) {
        if (request.mediaIds() == null || request.mediaIds().isEmpty()) {
            return;
        }
        List<MediaObject> mediaObjects = mediaRepository.findByIdInAndOwnerId(request.mediaIds(), authorId);
        if (mediaObjects.size() != new HashSet<>(request.mediaIds()).size()) {
            throw ApiException.forbidden("Görsellerden bazıları size ait değil ya da bulunamadı");
        }
        int order = 0;
        for (UUID mediaId : request.mediaIds()) {
            MediaObject media = mediaObjects.stream()
                    .filter(m -> m.getId().equals(mediaId))
                    .findFirst()
                    .orElseThrow();
            post.attachMedia(media, order++);
        }
    }

    private boolean likedByMe(UUID postId, UUID userId) {
        return userId != null && likeRepository.existsById(new PostLike.PostLikeId(postId, userId));
    }

    private boolean savedByMe(UUID postId, UUID userId) {
        return userId != null
                && saveRepository.existsById(
                        new com.waydee.social.domain.PostSave.PostSaveId(postId, userId));
    }

    private PostKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return PostKind.STANDARD;
        }
        try {
            return PostKind.valueOf(kind);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Geçersiz gönderi türü");
        }
    }

    private RsvpStatus parseRsvp(String status) {
        try {
            return RsvpStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("Geçersiz katılım durumu");
        }
    }

    private Post requireActivePost(UUID postId) {
        Post post = postRepository.findWithDetailsById(postId)
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
        if (post.isDeleted()) {
            throw ApiException.notFound("Gönderi bulunamadı");
        }
        return post;
    }

    private PostResponse toResponse(Post post, boolean likedByMe, boolean savedByMe,
                                    UUID myOptionId, String myRsvp) {
        List<String> mediaUrls = post.getMedia().stream()
                .map(pm -> pm.getMedia().publicUrl())
                .toList();

        PollView poll = null;
        EventView event = null;
        if (post.getKind() == PostKind.POLL) {
            List<PollOptionView> options = post.getPollOptions().stream()
                    .map(o -> new PollOptionView(o.getId(), o.getText(), o.getVoteCount()))
                    .toList();
            int total = options.stream().mapToInt(PollOptionView::voteCount).sum();
            poll = new PollView(options, total, myOptionId);
        } else if (post.getKind() == PostKind.EVENT) {
            event = new EventView(post.getEventTitle(), post.getEventLocation(), post.getEventStartsAt(),
                    post.getGoingCount(), post.getInterestedCount(), myRsvp);
        }

        return new PostResponse(
                post.getId(),
                post.getTerritoryId(),
                AuthorSummary.from(post.getAuthor()),
                post.getCaption(),
                mediaUrls,
                post.getLikeCount(),
                post.getCommentCount(),
                likedByMe,
                post.getSaveCount(),
                savedByMe,
                post.getCreatedAt(),
                post.getKind().name(),
                poll,
                event);
    }
}
