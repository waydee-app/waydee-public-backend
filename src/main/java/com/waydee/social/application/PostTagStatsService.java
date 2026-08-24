package com.waydee.social.application;

import com.waydee.common.error.ApiException;
import com.waydee.social.api.dto.TagStatsDtos.TagDaily;
import com.waydee.social.api.dto.TagStatsDtos.TagRow;
import com.waydee.social.api.dto.TagStatsDtos.TagStatsView;
import com.waydee.social.domain.Post;
import com.waydee.social.domain.PostTag;
import com.waydee.social.domain.PostTagDailyStat;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.social.infrastructure.PostTagRepository;
import com.waydee.social.infrastructure.PostTagStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>Etiket istatistikleri</b> — fotoğraf üzerindeki ürün etiketlerinin
 * gösterim ve tıklama raporu.
 *
 * <h2>Neden ayrı servis?</h2>
 * <p>{@link PostTagService} etiketin <b>içeriğini</b> yönetir (nerede duruyor,
 * hangi ürüne gidiyor). Burada ölçüm var. İkisini birleştirmek, her etiket
 * kaydında rapor bağımlılıklarını da taşımak demekti.
 */
@Service
@RequiredArgsConstructor
public class PostTagStatsService {

    /** Raporun varsayılan penceresi. */
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    private final PostTagStatsRepository statsRepository;
    private final PostTagRepository tagRepository;
    private final PostRepository postRepository;

    // ------------------------------------------------------------------ yazma

    /**
     * Gösterim kaydeder (etiket ekranda çizildi).
     *
     * <p>⚠️ Bir gönderinin etiketleri <b>toplu</b> bildirilir: fotoğraf açılınca
     * tek istek gider, etiket başına ayrı istek değil. Beş etiketli bir gönderi
     * her açılışta beş HTTP isteği üretseydi, ölçüm ölçtüğü şeyi yavaşlatırdı.
     *
     * <p>⚠️ Etiketlerin gerçekten <b>bu gönderiye ait</b> olduğu doğrulanır;
     * aksi hâlde herkes istediği etiketin sayacını şişirebilirdi.
     */
    @Transactional
    public void recordImpressions(UUID postId, List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var valid = tagRepository.findByPostIdOrderByPositionAsc(postId).stream()
                .map(PostTag::getId)
                .filter(tagIds::contains)
                .toList();
        for (UUID tagId : valid) {
            statsRepository.record(tagId, postId, today, 1, 0);
        }
    }

    /**
     * Tıklama kaydeder.
     *
     * <p>Toplam sayaç ({@code post_tags.click_count}) <b>ayrıca</b> artar —
     * kart üstünde tek sayı göstermek için tek satırlık okuma yeterli olmalı.
     * Bu ikilik bilinçli bir denormalizasyondur.
     */
    @Transactional
    public void recordClick(UUID tagId) {
        PostTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> ApiException.notFound("Etiket bulunamadı"));
        statsRepository.record(tagId, tag.getPostId(), LocalDate.now(ZoneOffset.UTC), 0, 1);
        tagRepository.recordClick(tagId);
    }

    // ------------------------------------------------------------------ okuma

    /**
     * Bir gönderinin etiket raporu.
     *
     * <p>🔒 Yalnız <b>gönderinin sahibi</b> görebilir: etiket performansı
     * ticari bir veridir ve ziyaretçiye açık değildir.
     */
    @Transactional(readOnly = true)
    public TagStatsView forPost(UUID postId, UUID viewerId, Integer days) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Gönderi bulunamadı"));
        if (!post.getAuthor().getId().equals(viewerId)) {
            throw ApiException.forbidden("Bu raporu görme yetkiniz yok");
        }

        int window = clampDays(days);
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(window - 1L);

        List<PostTag> tags = tagRepository.findByPostIdOrderByPositionAsc(postId);
        List<PostTagDailyStat> rows = statsRepository.forPostSince(postId, since);

        /* Etiket bazında toplam. */
        Map<UUID, int[]> totals = new LinkedHashMap<>();
        tags.forEach(t -> totals.put(t.getId(), new int[]{0, 0}));
        for (PostTagDailyStat s : rows) {
            totals.computeIfAbsent(s.getId().getTagId(), k -> new int[]{0, 0});
            int[] acc = totals.get(s.getId().getTagId());
            acc[0] += s.getImpressions();
            acc[1] += s.getClicks();
        }

        List<TagRow> tagRows = tags.stream().map(t -> {
            int[] acc = totals.getOrDefault(t.getId(), new int[]{0, 0});
            return new TagRow(t.getId(), t.getProductName(), t.getProductUrl(),
                    t.getPrice(), t.getCurrency(), acc[0], acc[1], ctr(acc[0], acc[1]));
        }).toList();

        return new TagStatsView(
                postId,
                window,
                tagRows.stream().mapToInt(TagRow::impressions).sum(),
                tagRows.stream().mapToInt(TagRow::clicks).sum(),
                ctr(tagRows.stream().mapToInt(TagRow::impressions).sum(),
                        tagRows.stream().mapToInt(TagRow::clicks).sum()),
                tagRows,
                dailySeries(rows, since, window));
    }

    /**
     * Günlük seri — <b>boş günler sıfırla doldurulur</b>.
     *
     * <p>⚠️ Yalnız veri olan günleri döndürmek, grafiği yanıltıcı yapardı:
     * çubuklar arasındaki boşluk "veri yok" değil "o gün olmadı" demektir ve
     * eksik günler eğriyi olduğundan düz gösterir.
     */
    private List<TagDaily> dailySeries(List<PostTagDailyStat> rows, LocalDate since, int window) {
        Map<LocalDate, int[]> byDay = new LinkedHashMap<>();
        for (int i = 0; i < window; i++) {
            byDay.put(since.plusDays(i), new int[]{0, 0});
        }
        for (PostTagDailyStat s : rows) {
            int[] acc = byDay.get(s.getId().getDay());
            if (acc != null) {
                acc[0] += s.getImpressions();
                acc[1] += s.getClicks();
            }
        }
        List<TagDaily> out = new ArrayList<>(byDay.size());
        byDay.forEach((day, acc) -> out.add(new TagDaily(day, acc[0], acc[1])));
        return out;
    }

    /** Tıklama oranı, yüzde — gösterim yoksa 0 (0'a bölme). */
    private static double ctr(int impressions, int clicks) {
        return impressions <= 0 ? 0 : Math.round(clicks * 10_000.0 / impressions) / 100.0;
    }

    private static int clampDays(Integer days) {
        if (days == null || days <= 0) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }
}
