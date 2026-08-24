package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.storage.MediaUrls;
import com.waydee.social.application.PostTagService;
import com.waydee.social.domain.PostTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fotoğraf üzerindeki ürün etiketleri.
 *
 * <p>⚠️ Konumlar <b>yüzde</b> (0–1) taşınır, piksel değil — bkz. {@link PostTag}.
 */
@Tag(name = "Post tags", description = "Fotoğraf üzerindeki ürün etiketleri")
@RestController
@RequestMapping("/api/v1/posts/{postId}/tags")
@RequiredArgsConstructor
public class PostTagController {

    private final PostTagService tagService;
    private final com.waydee.social.application.PostTagStatsService statsService;

    @Operation(summary = "Gönderinin etiketleri")
    @GetMapping
    public List<TagResponse> list(@PathVariable UUID postId) {
        return tagService.forPost(postId).stream().map(TagResponse::from).toList();
    }

    @Operation(summary = "Etiketleri tümüyle değiştir (yalnız sahibi)")
    @PutMapping
    public List<TagResponse> replace(@PathVariable UUID postId,
                                     @Valid @RequestBody ReplaceTagsRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        List<PostTagService.TagInput> inputs = request.tags().stream()
                .map(t -> new PostTagService.TagInput(
                        t.x(), t.y(), t.productUrl(), t.productName(),
                        t.price(), t.currency(), t.imageMediaId()))
                .toList();
        return tagService.replace(postId, user.id(), inputs).stream().map(TagResponse::from).toList();
    }

    /**
     * Etiket tıklandı.
     *
     * <p>🔴 15 Ağu 2026 — iki değişiklik:
     * <ol>
     *   <li>Artık {@link PostTagStatsService} üzerinden geçiyor: hem toplam
     *       sayaç ({@code post_tags.click_count}) hem <b>günlük istatistik</b>
     *       artıyor. Eskiden yalnız toplam artıyordu ve rapor "tıklama var ama
     *       hiçbir güne düşmemiş" gibi görünürdü.</li>
     *   <li>Oturum <b>istemiyor</b> ({@code SecurityConfig}'te permitAll).
     *       Paylaşılan bir fotoğrafı açan ziyaretçinin oturumu yoktur; kimlik
     *       istemek, ölçülmek istenen tıklamaların <b>çoğunu</b> kaybetmek
     *       demekti — istatistiğin var olma sebebi tam da onlar.</li>
     * </ol>
     */
    @Operation(summary = "Etiket tıklandı — sayaç ve günlük istatistik artar")
    @PostMapping("/{tagId}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void click(@PathVariable UUID postId, @PathVariable UUID tagId) {
        statsService.recordClick(tagId);
    }

    public record ReplaceTagsRequest(@Size(max = 20) List<TagItem> tags) {
    }

    public record TagItem(
            BigDecimal x,
            BigDecimal y,
            @NotBlank @Size(max = 500) String productUrl,
            @Size(max = 140) String productName,
            BigDecimal price,
            @Size(max = 3) String currency,
            UUID imageMediaId
    ) {
    }

    public record TagResponse(
            UUID id,
            BigDecimal x,
            BigDecimal y,
            String productUrl,
            String productName,
            BigDecimal price,
            String currency,
            String imageUrl,
            int position
    ) {
        static TagResponse from(PostTag tag) {
            return new TagResponse(
                    tag.getId(), tag.getX(), tag.getY(), tag.getProductUrl(),
                    tag.getProductName(), tag.getPrice(), tag.getCurrency(),
                    tag.getImageMediaId() == null ? null : MediaUrls.of(tag.getImageMediaId()),
                    tag.getPosition());
        }
    }
}
