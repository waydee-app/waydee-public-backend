package com.waydee.common.config;

import com.waydee.common.error.ApiException;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.common.persistence.AppSetting;
import com.waydee.common.persistence.AppSettingRepository;
import com.waydee.common.security.GoogleOAuthProperties;
import com.waydee.identity.application.PlanPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

/**
 * İstemciye açılan çalışma zamanı ayarları (şu an: Mapbox erişim anahtarı).
 *
 * **Neden DB'de:** anahtar eskiden yalnız `VITE_MAPBOX_TOKEN` ile derleme
 * zamanında gömülüyordu; değiştirmek için yeniden derleme + dağıtım gerekiyordu.
 * Artık `app_settings` tablosunda durur, admin panelden değiştirilir ve
 * değişiklik WS ile yayınlanınca açık istemciler haritayı yeni anahtarla
 * yeniden kurar.
 *
 * 🔒 **Yalnız GENEL (public) anahtar saklanır.** Mapbox'ın `pk.` ile başlayan
 * anahtarı tarayıcıya zaten açıktır. Gizli (`sk.`) anahtar buraya yazılamaz —
 * istemciye servis edilecek bir alana gizli anahtar konması sızıntı olurdu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientConfigService {

    public static final String MAPBOX_TOKEN_KEY = "mapbox.access_token";

    private final AppSettingRepository settingRepository;
    private final DomainEventPublisher eventPublisher;
    private final GoogleOAuthProperties googleOAuth;
    private final PlanPricingService planPricingService;

    /** Derleme/ortam yedeği — DB boşken kullanılır (yerel geliştirme bozulmasın). */
    @Value("${waydee.mapbox.access-token:}")
    private String fallbackToken;

    /**
     * İstemcinin kullanacağı anahtar: DB'deki değer, yoksa ortam yedeği.
     * Kimlik gerektirmez — vitrin haritası da bunu okur.
     */
    @Transactional(readOnly = true)
    public ClientConfig current() {
        String token = settingRepository.findById(MAPBOX_TOKEN_KEY)
                .map(AppSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(fallbackToken);
        Instant updatedAt = settingRepository.findById(MAPBOX_TOKEN_KEY)
                .map(AppSetting::getUpdatedAt)
                .orElse(null);
        // ⚠️ Google düğmesi yapılandırma EKSİKSE hiç çizilmemeli. Yarım kurulumda
        // düğmeyi göstermek, kullanıcıyı tıklayınca hata alan bir yola sokar.
        /* 🔴 Üyelik fiyatları da buradan iner: tanıtım sayfası fiyatı KODA
           GÖMÜLÜYDÜ ($10) — yönetimden değiştirilse bile vitrin eskisini
           gösteriyordu. Uç zaten kimliksizdir; fiyat herkese açık bir bilgidir.
           ⚠️ V37'den beri DÖRT hücre iner (plan × dönem); `proPrice` alanı eski
           istemciler için PRO AYLIK fiyatını taşımaya devam eder. */
        PlanPricingService.PricingTable prices = planPricingService.table();
        return new ClientConfig(token == null ? "" : token, updatedAt, googleOAuth.isConfigured(),
                prices.proMonthly(), prices.currency(), prices);
    }

    /** Yönetim ekranı için: anahtarın kendisi + kaynağı (DB mi ortam mı). */
    @Transactional(readOnly = true)
    public MapboxTokenView adminView() {
        return settingRepository.findById(MAPBOX_TOKEN_KEY)
                .map(s -> new MapboxTokenView(s.getValue(), "DATABASE", s.getUpdatedAt()))
                .orElseGet(() -> new MapboxTokenView(fallbackToken == null ? "" : fallbackToken, "ENV", null));
    }

    /**
     * Anahtarı günceller ve değişikliği yayınlar (açık istemciler anında yeniler).
     * Boş değer gönderilirse kayıt silinir → ortam yedeğine geri dönülür.
     */
    @Transactional
    public MapboxTokenView updateMapboxToken(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();

        if (token.isEmpty()) {
            settingRepository.findById(MAPBOX_TOKEN_KEY).ifPresent(settingRepository::delete);
            log.info("Mapbox anahtarı temizlendi — ortam yedeğine dönüldü");
            eventPublisher.publish(new ClientConfigChangedEvent(MAPBOX_TOKEN_KEY));
            return adminView();
        }

        validate(token);
        AppSetting setting = settingRepository.findById(MAPBOX_TOKEN_KEY).orElse(null);
        if (setting == null) {
            setting = settingRepository.save(new AppSetting(MAPBOX_TOKEN_KEY, token));
        } else {
            setting.update(token);
        }
        log.info("Mapbox anahtarı güncellendi (…{})", token.substring(Math.max(0, token.length() - 6)));
        eventPublisher.publish(new ClientConfigChangedEvent(MAPBOX_TOKEN_KEY));
        return new MapboxTokenView(setting.getValue(), "DATABASE", setting.getUpdatedAt());
    }

    /**
     * Anahtar biçim kontrolü.
     * ⚠️ `sk.` (gizli) anahtar REDDEDİLİR — bu değer her istemciye servis edilir.
     */
    private static void validate(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (lower.startsWith("sk.")) {
            throw ApiException.badRequest(
                    "Gizli (sk.) anahtar kullanılamaz — bu değer tarayıcıya gönderilir. Genel (pk.) anahtar girin.");
        }
        if (!lower.startsWith("pk.")) {
            throw ApiException.badRequest("Mapbox genel anahtarı 'pk.' ile başlamalıdır");
        }
        if (token.length() < 20 || token.length() > 500) {
            throw ApiException.badRequest("Anahtar uzunluğu geçersiz görünüyor");
        }
    }

    /**
     * İstemciye dönen ayarlar.
     *
     * @param proPrice    <b>geriye dönük</b> alan: PRO aylık fiyatı. Yeni
     *                    ekranlar {@code plans} tablosunu okur.
     * @param plans       plan × dönem fiyat tablosu (V37)
     */
    public record ClientConfig(String mapboxToken, Instant mapboxUpdatedAt, boolean googleAuthEnabled,
                               java.math.BigDecimal proPrice, String proCurrency,
                               PlanPricingService.PricingTable plans) {
    }

    /** Yönetim görünümü — `source`: DATABASE | ENV. */
    public record MapboxTokenView(String token, String source, Instant updatedAt) {
    }

    /** Ayar değişti — realtime katmanı bunu `/topic/config`'e taşır. */
    public record ClientConfigChangedEvent(String key) {
    }
}
