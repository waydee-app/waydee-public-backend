package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkInput;
import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView;
import com.waydee.identity.domain.SocialPlatform;
import com.waydee.identity.domain.UserSocialLink;
import com.waydee.identity.infrastructure.UserSocialLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Kullanıcının sosyal medya bağlantıları.
 *
 * Kullanıcı ister tam adres ister yalnız kullanıcı adı yazar; saklanan **ham
 * değerdir** (düzenlemeye dönünce yazdığını görür), dışarı verilirken platforma
 * göre tam URL üretilir.
 *
 * 🔒 Güvenlik: yalnız `http`/`https` kabul edilir — `javascript:` gibi şemalar
 * profil sayfasında tıklanabilir bağlantıya dönüşemez.
 */
@Service
@RequiredArgsConstructor
public class SocialLinkService {

    /** Kullanıcı adı verildiğinde adresin başına eklenecek önek. */
    private static final Map<SocialPlatform, String> HANDLE_PREFIX = new EnumMap<>(Map.ofEntries(
            Map.entry(SocialPlatform.INSTAGRAM, "https://instagram.com/"),
            Map.entry(SocialPlatform.X, "https://x.com/"),
            Map.entry(SocialPlatform.FACEBOOK, "https://facebook.com/"),
            Map.entry(SocialPlatform.YOUTUBE, "https://youtube.com/@"),
            Map.entry(SocialPlatform.TIKTOK, "https://tiktok.com/@"),
            Map.entry(SocialPlatform.SNAPCHAT, "https://snapchat.com/add/"),
            Map.entry(SocialPlatform.LINKEDIN, "https://linkedin.com/in/"),
            Map.entry(SocialPlatform.TELEGRAM, "https://t.me/"),
            Map.entry(SocialPlatform.GITHUB, "https://github.com/")));

    private static final int MAX_VALUE_LENGTH = 200;

    private final UserSocialLinkRepository repository;

    @Transactional(readOnly = true)
    public List<SocialLinkView> list(UUID userId) {
        return repository.findByUserIdOrderByPositionAsc(userId).stream()
                .map(SocialLinkService::toView)
                .toList();
    }

    /** Birden çok kullanıcının bağlantıları — N+1 olmadan (profil listeleri için). */
    @Transactional(readOnly = true)
    public Map<UUID, List<SocialLinkView>> listByUsers(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return repository.findByUserIdInOrderByPositionAsc(userIds).stream()
                .collect(Collectors.groupingBy(UserSocialLink::getUserId,
                        Collectors.mapping(SocialLinkService::toView, Collectors.toList())));
    }

    /**
     * Bağlantı listesini olduğu gibi değiştirir (tam değiştirme).
     * Boş değer gönderilen platform silinir; sıra, gelen listenin sırasıdır.
     */
    @Transactional
    public List<SocialLinkView> replace(UUID userId, List<SocialLinkInput> inputs) {
        List<UserSocialLink> existing = repository.findByUserIdOrderByPositionAsc(userId);
        Map<SocialPlatform, UserSocialLink> byPlatform = existing.stream()
                .collect(Collectors.toMap(UserSocialLink::getPlatform, l -> l, (a, b) -> a, () -> new EnumMap<>(SocialPlatform.class)));

        List<UserSocialLink> keep = new ArrayList<>();
        int position = 0;
        for (SocialLinkInput input : inputs == null ? List.<SocialLinkInput>of() : inputs) {
            if (input == null || input.platform() == null) {
                continue;
            }
            SocialPlatform platform = parsePlatform(input.platform());
            String value = input.value() == null ? "" : input.value().trim();
            if (value.isEmpty()) {
                continue; // boş = "bu platformu kaldır"
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                throw ApiException.badRequest("Bağlantı en fazla %d karakter olabilir".formatted(MAX_VALUE_LENGTH));
            }
            // Kaydetmeden önce üretilebilirliğini doğrula (geçersiz şema burada patlar).
            resolveUrl(platform, value);

            UserSocialLink link = byPlatform.remove(platform);
            if (link == null) {
                link = new UserSocialLink(userId, platform, value, position);
            } else {
                link.setValue(value);
                link.setPosition(position);
            }
            keep.add(link);
            position++;
        }

        // Listeye girmeyen eski platformlar silinir.
        if (!byPlatform.isEmpty()) {
            repository.deleteAll(byPlatform.values());
        }
        repository.saveAll(keep);
        return keep.stream().map(SocialLinkService::toView).toList();
    }

    private static SocialPlatform parsePlatform(String raw) {
        try {
            return SocialPlatform.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Bilinmeyen platform: " + raw);
        }
    }

    private static SocialLinkView toView(UserSocialLink link) {
        return new SocialLinkView(link.getPlatform().name(), link.getValue(),
                resolveUrl(link.getPlatform(), link.getValue()));
    }

    /** Ham değeri tam adrese çevirir; yalnız http/https döner. */
    static String resolveUrl(SocialPlatform platform, String rawValue) {
        String value = rawValue.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value;
        }
        // Buraya gelen değerde şema YOKTUR (http/https yukarıda döndü). İki nokta
        // görülüyorsa başka bir şema denenmiş demektir (javascript:, data: …) → reddet.
        if (value.indexOf(':') >= 0) {
            throw ApiException.badRequest("Yalnızca http/https adresleri eklenebilir");
        }

        String prefix = HANDLE_PREFIX.get(platform);
        if (prefix == null) {
            // WEBSITE: adres yazılmış ama şema unutulmuş.
            return "https://" + value;
        }
        // Kullanıcı adı: baştaki @ ve / temizlenir (kullanıcılar ikisini de yazıyor).
        String handle = value.replaceFirst("^[@/]+", "");
        if (handle.isEmpty()) {
            throw ApiException.badRequest("Geçersiz kullanıcı adı");
        }
        return prefix + handle;
    }
}
