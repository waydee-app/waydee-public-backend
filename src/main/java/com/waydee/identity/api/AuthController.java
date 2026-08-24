package com.waydee.identity.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.security.TurnstileService;
import com.waydee.identity.api.dto.AuthDtos.EmailOnlyRequest;
import com.waydee.identity.api.dto.AuthDtos.LoginRequest;
import com.waydee.identity.api.dto.AuthDtos.RefreshRequest;
import com.waydee.identity.api.dto.AuthDtos.RegisterRequest;
import com.waydee.identity.api.dto.AuthDtos.ResetPasswordRequest;
import com.waydee.identity.api.dto.AuthDtos.TokenResponse;
import com.waydee.identity.api.dto.AuthDtos.VerificationResultResponse;
import com.waydee.identity.api.dto.AuthDtos.VerifyEmailRequest;
import com.waydee.identity.application.AuthService;
import com.waydee.identity.application.EmailVerificationService;
import com.waydee.identity.application.TokenService;
import com.waydee.traffic.application.ClientInfo;
import com.waydee.traffic.application.TrafficService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Kayıt, giriş ve oturum yönetimi")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final TrafficService trafficService;
    private final ClientInfo clientInfo;
    private final TurnstileService turnstileService;
    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "Yeni kullanıcı kaydı")
    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletRequest http) {
        // Bot koruması şifre kontrolünden ÖNCE: sahte hesap ve deneme yağmuru burada durur.
        turnstileService.verify(request.turnstileToken(), clientInfo.ip(http));
        TokenResponse response = authService.register(request, clientInfo.ip(http), userAgent(http));
        track(http, response, request.username(), "app", true);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Giriş")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authenticate(request, http, "app", false);
    }

    @Operation(summary = "Yönetim uygulaması girişi — yalnızca ADMIN rolü")
    @PostMapping("/admin/login")
    public TokenResponse adminLogin(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authenticate(request, http, "admin", true);
    }

    @Operation(summary = "Access token yenileme (refresh rotasyonu)")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return tokenService.rotate(request.refreshToken(), clientInfo.ip(http), userAgent(http));
    }

    @Operation(summary = "Çıkış — refresh token iptali")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        tokenService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ e-posta doğrulama

    @Operation(summary = "E-posta doğrulama bağlantısını kullan (kayıt ya da adres değişimi)")
    @PostMapping("/verify-email")
    public VerificationResultResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                                  HttpServletRequest http) {
        return emailVerificationService.verify(request.token(), clientInfo.ip(http));
    }

    /**
     * Yanıt her durumda 204'tür: adres kayıtlı değilse ya da hesap zaten
     * doğrulanmışsa da aynı yanıt döner (e-posta enumeration'ı engellenir).
     */
    @Operation(summary = "Doğrulama bağlantısını tekrar gönder")
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody EmailOnlyRequest request, HttpServletRequest http) {
        turnstileService.verify(request.turnstileToken(), clientInfo.ip(http));
        emailVerificationService.resendVerification(request.email(), clientInfo.ip(http));
    }

    @Operation(summary = "Şifremi unuttum — sıfırlama bağlantısı gönderir")
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody EmailOnlyRequest request, HttpServletRequest http) {
        turnstileService.verify(request.turnstileToken(), clientInfo.ip(http));
        emailVerificationService.forgotPassword(request.email(), clientInfo.ip(http));
    }

    @Operation(summary = "Sıfırlama bağlantısıyla yeni şifre belirle (tüm oturumları kapatır)")
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        emailVerificationService.resetPassword(request.token(), request.newPassword(), clientInfo.ip(http));
    }

    /** Giriş + trafik kaydı (başarısızlar da kaydedilir → admin trafik raporu). */
    private TokenResponse authenticate(LoginRequest request, HttpServletRequest http,
                                       String surface, boolean adminOnly) {
        turnstileService.verify(request.turnstileToken(), clientInfo.ip(http));
        try {
            TokenResponse response = adminOnly
                    ? authService.adminLogin(request, clientInfo.ip(http), userAgent(http))
                    : authService.login(request, clientInfo.ip(http), userAgent(http));
            track(http, response, request.usernameOrEmail(), surface, true);
            return response;
        } catch (ApiException ex) {
            track(http, null, request.usernameOrEmail(), surface, false);
            throw ex;
        }
    }

    private void track(HttpServletRequest http, TokenResponse response, String fallbackName,
                       String surface, boolean success) {
        String ua = http.getHeader("User-Agent");
        trafficService.record(
                response != null ? response.user().id() : null,
                response != null ? response.user().username() : fallbackName,
                clientInfo.ip(http),
                clientInfo.country(http),
                clientInfo.device(ua),
                clientInfo.browser(ua),
                clientInfo.os(ua),
                surface,
                success,
                ua);
    }

    private String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
