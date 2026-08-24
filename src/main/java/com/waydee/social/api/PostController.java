package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.social.api.dto.SocialDtos.CommentResponse;
import com.waydee.social.api.dto.SocialDtos.CreateCommentRequest;
import com.waydee.social.api.dto.SocialDtos.CreatePostRequest;
import com.waydee.social.api.dto.SocialDtos.PollVoteRequest;
import com.waydee.social.api.dto.SocialDtos.PostResponse;
import com.waydee.social.api.dto.SocialDtos.RsvpRequest;
import com.waydee.social.application.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Posts", description = "Gönderi, beğeni ve yorumlar")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "Alana gönderi ekle (yalnızca sahibi)")
    @PostMapping("/territories/{territoryId}/posts")
    public ResponseEntity<PostResponse> create(@PathVariable UUID territoryId,
                                               @AuthenticationPrincipal AuthenticatedUser principal,
                                               @Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.create(territoryId, principal.id(), request));
    }

    @Operation(summary = "Alanın gönderileri")
    @GetMapping("/territories/{territoryId}/posts")
    public PageResponse<PostResponse> list(@PathVariable UUID territoryId,
                                           @AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "12") int size) {
        return postService.list(territoryId, principal != null ? principal.id() : null, page, size);
    }

    @Operation(summary = "Bir kullanıcının gönderileri (profil ızgarası)")
    @GetMapping("/users/{userId}/posts")
    public PageResponse<PostResponse> byAuthor(@PathVariable UUID userId,
                                               @AuthenticationPrincipal AuthenticatedUser principal,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "12") int size) {
        return postService.byAuthor(userId, principal != null ? principal.id() : null, page, size);
    }

    @Operation(summary = "Tek gönderi (detay ekranı)")
    @GetMapping("/posts/{postId}")
    public PostResponse one(@PathVariable UUID postId,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return postService.one(postId, principal != null ? principal.id() : null);
    }

    @Operation(summary = "Gönderiyi beğen")
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Void> like(@PathVariable UUID postId,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.like(postId, principal.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Beğeniyi geri al")
    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlike(@PathVariable UUID postId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.unlike(postId, principal.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * <b>Gönderiyi kaydet</b> (yer imi).
     *
     * <p>⚠️ Beğeniyle aynı biçim: <b>POST = kaydet, DELETE = geri al</b>. Tek bir
     * "toggle" ucu, çift dokunuşta hangi durumun kaldığını belirsiz bırakırdı —
     * ağ gecikmesinde iki istek ters sırayla varabilir.
     */
    @Operation(summary = "Gönderiyi kaydet")
    @PostMapping("/posts/{postId}/save")
    public ResponseEntity<Void> save(@PathVariable UUID postId,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.save(postId, principal.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Kaydetmeyi geri al")
    @DeleteMapping("/posts/{postId}/save")
    public ResponseEntity<Void> unsave(@PathVariable UUID postId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.unsave(postId, principal.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * <b>Beğendiklerim / Kaydettiklerim.</b>
     *
     * <p>🔒 Kimlik <b>oturumdan</b> alınır; istekte kullanıcı kimliği kabul
     * edilmez. Aksi halde herkes başkasının kaydettiklerini okuyabilirdi —
     * kaydetme gizli bir listedir, beğeni sayısı gibi herkese açık değildir.
     */
    @Operation(summary = "Beğendiğim gönderiler")
    @GetMapping("/users/me/liked-posts")
    public PageResponse<PostResponse> likedPosts(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        return PageResponse.from(postService.likedPosts(principal.id(),
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 50))), r -> r);
    }

    @Operation(summary = "Kaydettiğim gönderiler")
    @GetMapping("/users/me/saved-posts")
    public PageResponse<PostResponse> savedPosts(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        return PageResponse.from(postService.savedPosts(principal.id(),
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 50))), r -> r);
    }

    @Operation(summary = "Ankete oy ver")
    @PostMapping("/posts/{postId}/poll/vote")
    public PostResponse vote(@PathVariable UUID postId,
                             @AuthenticationPrincipal AuthenticatedUser principal,
                             @Valid @RequestBody PollVoteRequest request) {
        return postService.vote(postId, request.optionId(), principal.id());
    }

    @Operation(summary = "Etkinliğe katıl / geri al")
    @PostMapping("/posts/{postId}/event/rsvp")
    public PostResponse rsvp(@PathVariable UUID postId,
                             @AuthenticationPrincipal AuthenticatedUser principal,
                             @Valid @RequestBody RsvpRequest request) {
        return postService.rsvp(postId, principal.id(), request.status());
    }

    @Operation(summary = "Yorum ekle")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> comment(@PathVariable UUID postId,
                                                   @AuthenticationPrincipal AuthenticatedUser principal,
                                                   @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.comment(postId, principal.id(), request));
    }

    @Operation(summary = "Gönderi yorumları")
    @GetMapping("/posts/{postId}/comments")
    public PageResponse<CommentResponse> comments(@PathVariable UUID postId,
                                                  @AuthenticationPrincipal AuthenticatedUser principal,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return postService.comments(postId, principal.id(), principal.isAdmin(), page, size);
    }

    @Operation(summary = "Yorumu sil (yorumu yazan · gönderi sahibi · admin)")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID commentId,
                                              @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.deleteComment(commentId, principal.id(), principal.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gönderiyi sil (sahibi ya da admin)")
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@PathVariable UUID postId,
                                       @AuthenticationPrincipal AuthenticatedUser principal) {
        postService.delete(postId, principal.id(), principal.isAdmin());
        return ResponseEntity.noContent().build();
    }
}
