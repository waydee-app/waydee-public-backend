package com.waydee.identity.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.GoogleOAuthProperties;
import com.waydee.identity.api.dto.AuthDtos.TokenResponse;
import com.waydee.identity.application.GoogleAuthService;
import com.waydee.identity.infrastructure.GoogleOAuthClient;
import com.waydee.common.security.OAuthStateSigner;
import com.waydee.traffic.application.ClientInfo;
import com.waydee.traffic.application.TrafficService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Google ile giriş / kayıt uçları.
 *
 * <p>Akış üç adımdır:
 * <ol>
 *   <li>{@code GET /auth/google/start} → tarayıcı Google'a yönlendirilir</li>
 *   <li>{@code GET /auth/google/callback} → Google kod ile döner; sunucu kodu
 *       oturuma çevirir ve <b>tek kullanımlık bilet</b> ile arayüze yönlendirir</li>
 *   <li>{@code POST /auth/google/exchange} → arayüz bileti jetona çevirir</li>
 * </ol>
 *
 * <p>⚠️ Jetonlar hiçbir zaman yönlendirme adresinde taşınmaz — adres çubuğu,
 * tarayıcı geçmişi ve {@code Referer} başlığı sızıntı yüzeyidir.
 */
@Slf4j
@Tag(name = "Auth · Google", description = "Google ile giriş / kayıt")
@RestController
@RequestMapping("/api/v1/auth/google")
@RequiredArgsConstructor
@EnableConfigurationProperties(GoogleOAuthProperties.class)
public class GoogleAuthController {

    private final GoogleAuthService googleAuthService;
    private final GoogleOAuthClient googleClient;
    private final GoogleOAuthProperties properties;
    private final OAuthStateSigner stateSigner;
    private final TrafficService trafficService;
    private final ClientInfo clientInfo;

    @Operation(summary = "Google onay ekranına yönlendir")
    @GetMapping("/start")
    public ResponseEntity<Void> start(
            @RequestParam(required = false) String callbackUrl,
            @RequestParam(required = false) String loginHint) {
        requireEnabled();
        // callbackUrl KULLANICIDAN gelir → doğrulanmadan state'e konmaz.
        // Aksi halde akış sonunda kullanıcı başka bir siteye yönlendirilebilirdi
        // (açık yönlendirme / kimlik avı).
        String safeCallback = sanitizeCallback(callbackUrl);
        String state = stateSigner.issue(Map.of("cb", safeCallback), properties.stateTtl());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(googleClient.authorizationUrl(state, loginHint)))
                .build();
    }

    @Operation(summary = "Google dönüşü — bilet ile arayüze yönlendirir")
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest http) {
        requireEnabled();

        Optional<Map<String, String>> payload = stateSigner.verify(state);
        // State doğrulanamıyorsa akışı BİZ başlatmamışız demektir (CSRF) —
        // kullanıcının kendi hedefine değil, güvenli varsayılana döneriz.
        String callback = payload.map(p -> p.getOrDefault("cb", "")).orElse("");

        if (error != null && !error.isBlank()) {
            // Kullanıcı Google ekranında "İptal" dedi: hata değil, sessizce dön.
            log.info("Google akışı kullanıcı tarafından iptal edildi: {}", error);
            return redirectToApp(callback, null, "cancelled");
        }
        if (payload.isEmpty()) {
            return redirectToApp("", null, "state");
        }
        if (code == null || code.isBlank()) {
            return redirectToApp(callback, null, "state");
        }

        String ip = clientInfo.ip(http);
        String ua = http.getHeader("User-Agent");
        try {
            TokenResponse session = googleAuthService.completeLogin(code, ip, userAgent(http));
            trafficService.record(session.user().id(), session.user().username(), ip,
                    clientInfo.country(http), clientInfo.device(ua), clientInfo.browser(ua),
                    clientInfo.os(ua), "google", true, ua);
            String ticket = googleAuthService.issueTicket(session.user().id(), ip);
            return redirectToApp(callback, ticket, null);
        } catch (ApiException ex) {
            trafficService.record(null, "google", ip, clientInfo.country(http),
                    clientInfo.device(ua), clientInfo.browser(ua), clientInfo.os(ua),
                    "google", false, ua);
            log.warn("Google girişi tamamlanamadı: {}", ex.getMessage());
            return redirectToApp(callback, null, ex.getCode().name());
        }
    }

    @Operation(summary = "Tek kullanımlık bileti oturum jetonuna çevir")
    @PostMapping("/exchange")
    public TokenResponse exchange(@Valid @RequestBody ExchangeRequest request, HttpServletRequest http) {
        requireEnabled();
        return googleAuthService.redeemTicket(request.ticket(), clientInfo.ip(http), userAgent(http));
    }

    // ------------------------------------------------------------------ yardımcılar
    private void requireEnabled() {
        if (!googleAuthService.isEnabled()) {
            // 503: özellik var ama bu kurulumda yapılandırılmamış. Arayüz zaten
            // /config'e bakıp düğmeyi çizmez; bu, doğrudan çağrılara karşı kapıdır.
            throw new ApiException(ErrorCode.NOT_FOUND, "Google ile giriş bu kurulumda kapalı");
        }
    }

    /**
     * Dönüş adresini güvenli hale getirir.
     *
     * <p>⚠️ <b>Açık yönlendirme kapısı.</b> Yalnız <b>kendi sitemizde</b> bir
     * yol kabul edilir: {@code /} ile başlamalı ve {@code //} ya da {@code \\}
     * ile başlamamalıdır (bunlar protokol-göreli mutlak adreslerdir ve
     * {@code //kotu-site.com} şeklinde dışarı çıkarırdı).
     */
    private String sanitizeCallback(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return "";
        }
        String value = callbackUrl.trim();
        if (!value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
            return "";
        }
        return value.length() > 200 ? "" : value;
    }

    private ResponseEntity<Void> redirectToApp(String callback, String ticket, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.appBaseUrl())
                .path("/auth/google");
        if (ticket != null) {
            builder.queryParam("ticket", ticket);
        }
        if (error != null) {
            builder.queryParam("error", error);
        }
        if (callback != null && !callback.isBlank()) {
            builder.queryParam("next", callback);
        }
        // Elle URLEncoder + `build(true)` ikilisi kırılgandı: kodlama iki yerde
        // yapılırsa ya çift kodlanır ya da kodlanmamış bir karakter patlatır.
        // Tek kural: ham değer ver, kodlamayı builder yapsın.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(builder.build().encode().toUriString()))
                .build();
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }

    public record ExchangeRequest(@NotBlank @Size(max = 200) String ticket) {
    }
}
