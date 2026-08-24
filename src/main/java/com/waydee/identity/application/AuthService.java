package com.waydee.identity.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.ratelimit.LoginAttemptGuard;
import com.waydee.identity.api.dto.AuthDtos.LoginRequest;
import com.waydee.identity.api.dto.AuthDtos.RegisterRequest;
import com.waydee.identity.api.dto.AuthDtos.TokenResponse;
import com.waydee.identity.domain.Role;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditRecorder auditRecorder;
    private final LoginAttemptGuard loginAttemptGuard;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public TokenResponse register(RegisterRequest request, String ip, String userAgent) {
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN, "Bu kullanıcı adı zaten alınmış");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "Bu e-posta ile kayıtlı bir hesap var");
        }

        User user = userRepository.save(new User(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                Role.USER));
        user.markLogin();

        auditRecorder.record(user.getId(), username, "USER_REGISTERED", "USER",
                user.getId().toString(), null, ip);
        // Doğrulama bağlantısı kayıtla aynı transaction'da üretilir; gönderim
        // asenkrondur, yani SMTP arızası kaydı geri almaz (bkz. MailService).
        emailVerificationService.sendVerification(user, ip);
        return tokenService.issue(user, ip, userAgent);
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String ip, String userAgent) {
        return authenticate(request, ip, userAgent, false);
    }

    /**
     * Yönetim uygulamasının giriş ucu. USER rolündeki hesaplara token üretilmez —
     * yetkisiz hesap admin uygulamasında hiçbir zaman oturum açamaz.
     */
    @Transactional
    public TokenResponse adminLogin(LoginRequest request, String ip, String userAgent) {
        return authenticate(request, ip, userAgent, true);
    }

    private TokenResponse authenticate(LoginRequest request, String ip, String userAgent, boolean adminOnly) {
        String identifier = request.usernameOrEmail().toLowerCase(Locale.ROOT).trim();
        // Hesap bazlı brute-force kilidi: IP limitinden bağımsız, header spoof'uyla atlanamaz.
        loginAttemptGuard.check(identifier);
        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> {
                    loginAttemptGuard.onFailure(identifier);
                    return invalidCredentials();
                });

        // ⚠️ Google ile açılan hesabın ŞİFRESİ YOKTUR. Bu durum `matches()`
        // çağrısından ÖNCE ele alınmalıdır: null hash ile devam etmek NPE ya da
        // (kütüphaneye göre) beklenmedik bir "eşleşti" sonucu üretebilir.
        // Kullanıcıya nötr değil AÇIK bir mesaj veriyoruz — hesabın var olduğu
        // zaten Google düğmesinden anlaşılıyor, buradaki belirsizlik yalnızca
        // gerçek kullanıcıyı kilitler ("şifremi unuttum" da işe yaramaz).
        if (!user.hasPassword()) {
            loginAttemptGuard.onFailure(identifier);
            auditRecorder.record(user.getId(), user.getUsername(), "LOGIN_FAILED", "USER",
                    user.getId().toString(),
                    Map.of("reason", "no_password", "surface", adminOnly ? "admin" : "app"), ip);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    "Bu hesap Google ile açılmış. \"Google ile devam et\" düğmesini kullan.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptGuard.onFailure(identifier);
            auditRecorder.record(null, identifier, "LOGIN_FAILED", "USER", null,
                    Map.of("reason", "wrong_password", "surface", adminOnly ? "admin" : "app"), ip);
            throw invalidCredentials();
        }
        loginAttemptGuard.onSuccess(identifier);
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "Hesabınız askıya alınmış");
        }
        if (adminOnly && user.getRole() != Role.ADMIN) {
            auditRecorder.record(user.getId(), user.getUsername(), "ADMIN_LOGIN_DENIED", "USER",
                    user.getId().toString(), Map.of("role", user.getRole().name()), ip);
            // Hesabın var olduğunu sızdırmamak için kimlik hatasıyla aynı yanıt döner.
            throw invalidCredentials();
        }

        user.markLogin();
        auditRecorder.record(user.getId(), user.getUsername(), adminOnly ? "ADMIN_LOGIN" : "LOGIN", "USER",
                user.getId().toString(), null, ip);
        return tokenService.issue(user, ip, userAgent);
    }

    private ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Kullanıcı adı veya şifre hatalı");
    }
}
