package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostTag;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.PostTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fotoğraf üzerindeki ürün etiketleri.
 *
 * <p>Etiketler gönderiyle birlikte <b>tümü birden</b> yazılır (replace):
 * kullanıcı görselin üstünde etiketleri sürükleyip siliyor, tek tek
 * ekle/güncelle/sil uçları hem daha fazla istek hem de yarım kalmış bir
 * durum (bir etiket kaydedildi, diğeri kaydedilmedi) üretirdi.
 */
@Service
@RequiredArgsConstructor
public class PostTagService {

    /** Bir fotoğrafta makul üst sınır — ücretsiz planda ayrıca 3'e iner. */
    private static final int MAX_TAGS = 20;

    /** `post_tags.product_url` sütununun genişliği (V29). Tek kaynak burası. */
    private static final int PRODUCT_URL_MAX = 500;

    private final PostTagRepository tagRepository;
    private final PostRepository postRepository;
    private final MediaService mediaService;
    private final HtmlSanitizer htmlSanitizer;

    @Transactional(readOnly = true)
    public List<PostTag> forPost(UUID postId) {
        return tagRepository.findByPostIdOrderByPositionAsc(postId);
    }

    /**
     * Gönderinin etiketlerini tümüyle değiştirir.
     *
     * @param ownerId işlemi yapan kullanıcı — sahiplik burada doğrulanır
     */
    @Transactional
    public List<PostTag> replace(UUID postId, UUID ownerId, List<TagInput> inputs) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
        if (post.getDeletedAt() != null) {
            throw ApiException.notFound("Gönderi bulunamadı");
        }
        // ⚠️ Sahiplik kontrolü sunucuda: istemcinin düğmeyi gizlemesi güvenlik değildir.
        if (!post.getAuthor().getId().equals(ownerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bu gönderiyi düzenleyemezsin");
        }
        if (inputs.size() > MAX_TAGS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Bir fotoğrafta en fazla " + MAX_TAGS + " etiket olabilir");
        }

        tagRepository.deleteByPostId(postId);
        List<PostTag> saved = new ArrayList<>();
        int position = 0;
        for (TagInput in : inputs) {
            // 🔒 Yalnız http/https — `javascript:` gibi şemalar tıklanabilir
            // zararlı bağlantı üretirdi (sosyal bağlantılarla aynı kural).
            /*
             * ⚠️ Üst sınır SÜTUNLA aynı (500) — varsayılan 200 değil.
             * Gerçek pazaryeri ürün adresleri izleme parametreleriyle 200'ü
             * rutin aşıyor ve kullanıcı bu yüzden etiket ekleyemiyordu.
             */
            String url = htmlSanitizer.normalizeWebsite(in.productUrl(), PRODUCT_URL_MAX);
            if (url == null || url.isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ürün bağlantısı geçersiz");
            }
            PostTag tag = new PostTag(postId, clamp(in.x()), clamp(in.y()), url, position++);
            tag.setProductName(trim(in.productName(), 140));
            tag.setPrice(in.price());
            tag.setCurrency(in.currency() == null ? null
                    : in.currency().toUpperCase(Locale.ROOT).trim());
            if (in.imageMediaId() != null) {
                // Başkasının medyası etiket görseli yapılamaz (avatar IDOR'unun aynısı).
                mediaService.assertOwnedBy(in.imageMediaId(), ownerId);
                tag.setImageMediaId(in.imageMediaId());
            }
            saved.add(tagRepository.save(tag));
        }
        post.setTagCount(saved.size());
        return saved;
    }

    /* ══════════════════════════════════════════════════════════════════════
     * 🔴 17 Ağu 2026 — TEKİL EKLE / GÜNCELLE / SİL
     *
     * <b>Neden {@link #replace} yetmiyordu:</b> o metot {@code deleteByPostId}
     * ile TÜM satırları siler ve yenilerini <b>YENİ kimliklerle</b> yazar.
     * Oysa {@code post_tag_daily_stats.tag_id} etiket satırına
     * {@code ON DELETE CASCADE} ile bağlıdır (V44).
     *
     * Sonuç — <b>ölçülebilir bir veri kaybı</b>: bir gönderiye ikinci etiketi
     * eklemek, birinci etiketin o güne kadar biriken <b>gösterim ve tıklama
     * istatistiğini siliyordu</b>. Kullanıcı bunu asla göremezdi; rapor
     * çalışıyor görünür, yalnız sayılar sessizce sıfırlanırdı.
     *
     * Bu üç metot satır kimliğini <b>korur</b>, dolayısıyla istatistik yaşar.
     * ⚠️ Doğrulama tek yerde: {@link #applyInput}. Üç yol da oradan geçer,
     * yoksa şema/uzunluk/IDOR kontrollerinden biri er geç bir yolda unutulurdu.
     * ══════════════════════════════════════════════════════════════════════ */

    /**
     * Gönderinin <b>sonuna</b> tek etiket ekler — mevcut satırlara dokunmaz.
     *
     * @return eklenen etiket
     */
    @Transactional
    public PostTag addOne(UUID postId, UUID ownerId, TagInput input) {
        Post post = requireOwned(postId, ownerId);
        List<PostTag> existing = tagRepository.findByPostIdOrderByPositionAsc(postId);
        if (existing.size() + 1 > MAX_TAGS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Bir fotoğrafta en fazla " + MAX_TAGS + " etiket olabilir");
        }
        PostTag tag = new PostTag(postId, clamp(input.x()), clamp(input.y()),
                requireUrl(input.productUrl()), existing.size());
        applyInput(tag, input, ownerId);
        PostTag saved = tagRepository.save(tag);
        post.setTagCount(existing.size() + 1);
        return saved;
    }

    /**
     * Tek etiketi günceller — <b>kimliği ve dolayısıyla istatistiği korur</b>.
     *
     * <p>⚠️ Etiketin gerçekten bu gönderiye ait olduğu doğrulanır. Yalnız
     * kimliğe bakmak, başka bir gönderinin etiketini bu gönderi üzerinden
     * düzenlemeye izin verirdi (IDOR).
     */
    @Transactional
    public PostTag updateOne(UUID postId, UUID tagId, UUID ownerId, TagInput input) {
        requireOwned(postId, ownerId);
        PostTag tag = requireTagOfPost(postId, tagId);
        tag.setX(clamp(input.x()));
        tag.setY(clamp(input.y()));
        tag.setProductUrl(requireUrl(input.productUrl()));
        applyInput(tag, input, ownerId);
        return tagRepository.save(tag);
    }

    /** Tek etiketi siler ve kalanların sırasını yeniden numaralar. */
    @Transactional
    public void deleteOne(UUID postId, UUID tagId, UUID ownerId) {
        Post post = requireOwned(postId, ownerId);
        PostTag tag = requireTagOfPost(postId, tagId);
        tagRepository.delete(tag);
        // Sıra boşluklu kalmamalı: konum, arayüzün çizim sırasıdır.
        List<PostTag> rest = tagRepository.findByPostIdOrderByPositionAsc(postId).stream()
                .filter(t -> !t.getId().equals(tagId))
                .toList();
        int position = 0;
        for (PostTag t : rest) {
            t.setPosition(position++);
        }
        post.setTagCount(rest.size());
    }

    /**
     * Ad/fiyat/para birimi/görsel alanlarını doğrulayarak yazar.
     * <p>🔒 Başkasının medyası etiket görseli yapılamaz (avatar IDOR'unun aynısı).
     */
    private void applyInput(PostTag tag, TagInput in, UUID ownerId) {
        tag.setProductName(trim(in.productName(), 140));
        tag.setPrice(in.price());
        tag.setCurrency(in.currency() == null ? null
                : in.currency().toUpperCase(Locale.ROOT).trim());
        if (in.imageMediaId() != null) {
            mediaService.assertOwnedBy(in.imageMediaId(), ownerId);
            tag.setImageMediaId(in.imageMediaId());
        } else {
            // Görsel kaldırıldıysa gerçekten kaldırılmalı; aksi halde
            // düzenlemede "sil" işlemi sessizce yok sayılırdı.
            tag.setImageMediaId(null);
        }
    }

    /** 🔒 Yalnız http/https — sütun genişliğiyle aynı üst sınır. */
    private String requireUrl(String raw) {
        String url = htmlSanitizer.normalizeWebsite(raw, PRODUCT_URL_MAX);
        if (url == null || url.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ürün bağlantısı geçersiz");
        }
        return url;
    }

    private Post requireOwned(UUID postId, UUID ownerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
        if (post.getDeletedAt() != null) {
            throw ApiException.notFound("Gönderi bulunamadı");
        }
        if (!post.getAuthor().getId().equals(ownerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Bu gönderiyi düzenleyemezsin");
        }
        return post;
    }

    private PostTag requireTagOfPost(UUID postId, UUID tagId) {
        PostTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> ApiException.notFound("Etiket bulunamadı"));
        if (!tag.getPostId().equals(postId)) {
            // 404, 403 değil: başka bir gönderinin etiketinin VARLIĞI bile sızmasın
            // (fatura ve stant uçlarındaki kuralın aynısı).
            throw ApiException.notFound("Etiket bulunamadı");
        }
        return tag;
    }

    /** Etiket tıklaması — sayaç atomik artar (Analytics için). */
    @Transactional
    public void recordClick(UUID tagId) {
        tagRepository.recordClick(tagId);
    }

    /**
     * Konumu 0–1 aralığına sıkıştırır.
     *
     * <p>⚠️ İstemciden gelen değere güvenilmez: görselin dışına düşen bir
     * etiket hiçbir zaman çizilemez ve DB kısıtını da ihlal ederdi.
     */
    private static BigDecimal clamp(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return value;
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : (v.length() > max ? v.substring(0, max) : v);
    }

    /** İstemciden gelen etiket girdisi. */
    public record TagInput(
            BigDecimal x,
            BigDecimal y,
            String productUrl,
            String productName,
            BigDecimal price,
            String currency,
            UUID imageMediaId
    ) {
    }
}
