package com.waydee.identity.application;

import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.mail.EmailTemplates;
import com.waydee.common.mail.MailService;
import com.waydee.identity.api.dto.AuthDtos.VerificationResultResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.VerificationPurpose;
import com.waydee.identity.domain.VerificationToken;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.identity.infrastructure.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * E-posta doğrulama, e-posta değişimi ve şifre sıfırlama akışları.
 *
 * <p><b>Kimlik sızdırmama:</b> "tekrar gönder" ve "şifremi unuttum" uçları adres
 * kayıtlı olsa da olmasa da <b>aynı yanıtı</b> verir. Aksi halde bu uçlar bir
 * e-posta enumeration aracına dönerdi.
 *
 * <p><b>Jeton kuralları:</b> 256 bit rastgele, DB'de yalnız SHA-256 özeti,
 * tek kullanımlık, süreli ve amaç bazlı. Yeni jeton üretilince aynı amaçtaki
 * eski jetonlar geçersizleşir — gelen kutusunda biriken eski postalar çalışan
 * anahtar olarak kalmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    /** Kısa pencerede kaç bağlantı istenebilir (spam ve posta kutusu yağmuru koruması). */
    private static final int MAX_SENDS_PER_WINDOW = 3;
    private static final Duration SEND_WINDOW = Duration.ofMinutes(10);

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final MailService mailService;
    private final EmailTemplates templates;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditRecorder auditRecorder;
    private final SecureRandom random = new SecureRandom();

    // ------------------------------------------------------------ kayıt doğrulama

    /** Kayıt akışının sonunda çağrılır (aynı transaction içinde). */
    @Transactional
    public void sendVerification(User user, String ip) {
        String raw = issueToken(user.getId(), VerificationPurpose.EMAIL_VERIFY, null,
                mailService.properties().verificationTtl(), ip);
        mailService.send(user.getEmail(), templates.verification(
                user.getDisplayName(), mailService.properties().verifyUrl(raw),
                mailService.properties().verificationTtl()));
    }

    /**
     * Doğrulama bağlantısını yeniden gönderir.
     *
     * <p>Yanıt her zaman aynıdır: adres kayıtlı değilse ya da hesap zaten
     * doğrulanmışsa sessizce hiçbir şey yapılmaz.
     */
    @Transactional
    public void resendVerification(String email, String ip) {
        Optional<User> found = userRepository.findByEmail(normalize(email));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (user.isEmailVerified() || !user.isActive()) {
            return;
        }
        guardSendRate(user.getId(), VerificationPurpose.EMAIL_VERIFY);
        sendVerification(user, ip);
    }

    // ------------------------------------------------------------ e-posta değişimi

    /**
     * Yeni adrese doğrulama bağlantısı gönderir. Adres, bağlantıya tıklanana
     * kadar {@code users} tablosuna <b>yazılmaz</b> — yanlış yazılan bir adres
     * hesabı erişilemez hâle getirmemelidir.
     */
    @Transactional
    public String requestEmailChange(UUID userId, String newEmail, String currentPassword, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        // ⚠️ Şifresiz (Google) hesapta şifre onayı yapılamaz. Adres değişimi bu
        // hesaplar için kapalıdır: adres Google hesabının kimliğidir, buradan
        // değiştirilirse iki taraf birbirini tutmaz.
        if (!user.hasPassword()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Google ile açılan hesabın adresi buradan değiştirilemez.");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Şifre hatalı");
        }
        String email = normalize(newEmail);
        if (email.equals(user.getEmail())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bu adres zaten hesabına tanımlı");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "Bu e-posta ile kayıtlı bir hesap var");
        }
        guardSendRate(user.getId(), VerificationPurpose.EMAIL_CHANGE);

        String raw = issueToken(user.getId(), VerificationPurpose.EMAIL_CHANGE, email,
                mailService.properties().verificationTtl(), ip);
        mailService.send(email, templates.emailChange(
                user.getDisplayName(), email, mailService.properties().verifyUrl(raw),
                mailService.properties().verificationTtl()));
        auditRecorder.record(user.getId(), user.getUsername(), "EMAIL_CHANGE_REQUESTED", "USER",
                user.getId().toString(), Map.of("target", email), ip);
        return email;
    }

    // ------------------------------------------------------------ doğrulama (tıklama)

    /**
     * Doğrulama bağlantısını tüketir. Hem kayıt doğrulaması hem e-posta değişimi
     * bu uçtan geçer — istemci hangi akış olduğunu yanıttaki {@code purpose}'tan
     * anlar.
     */
    @Transactional
    public VerificationResultResponse verify(String rawToken, String ip) {
        VerificationToken token = consume(rawToken, null);
        if (token.getPurpose() == VerificationPurpose.PASSWORD_RESET) {
            // Şifre sıfırlama jetonu buradan tüketilemez (yanlış akış).
            throw invalidToken();
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(EmailVerificationService::invalidToken);

        if (token.getPurpose() == VerificationPurpose.EMAIL_CHANGE) {
            String target = token.getTargetEmail();
            // Bağlantı beklerken adres başkasına verilmiş olabilir.
            if (target == null || (!target.equals(user.getEmail()) && userRepository.existsByEmail(target))) {
                throw new ApiException(ErrorCode.EMAIL_TAKEN, "Bu e-posta ile kayıtlı bir hesap var");
            }
            user.changeVerifiedEmail(target);
            auditRecorder.record(user.getId(), user.getUsername(), "EMAIL_CHANGED", "USER",
                    user.getId().toString(), Map.of("email", target), ip);
        } else {
            user.markEmailVerified();
            auditRecorder.record(user.getId(), user.getUsername(), "EMAIL_VERIFIED", "USER",
                    user.getId().toString(), null, ip);
            // Doğrulama bitti: gelen kutusunda kalan diğer bağlantılar da ölsün.
            tokenRepository.invalidateOpenTokens(user.getId(), VerificationPurpose.EMAIL_VERIFY, Instant.now());
            mailService.send(user.getEmail(),
                    templates.welcome(user.getDisplayName(), mailService.properties().baseUrl()));
        }
        return new VerificationResultResponse(token.getPurpose().name(), user.getEmail());
    }

    // ------------------------------------------------------------ şifre sıfırlama

    /** Şifremi unuttum. Adres kayıtlı olmasa da yanıt aynıdır (enumeration yok). */
    @Transactional
    public void forgotPassword(String email, String ip) {
        Optional<User> found = userRepository.findByEmail(normalize(email));
        if (found.isEmpty() || !found.get().isActive()) {
            return;
        }
        User user = found.get();
        guardSendRate(user.getId(), VerificationPurpose.PASSWORD_RESET);
        String raw = issueToken(user.getId(), VerificationPurpose.PASSWORD_RESET, null,
                mailService.properties().passwordResetTtl(), ip);
        mailService.send(user.getEmail(), templates.passwordReset(
                user.getDisplayName(), mailService.properties().resetUrl(raw),
                mailService.properties().passwordResetTtl()));
        auditRecorder.record(user.getId(), user.getUsername(), "PASSWORD_RESET_REQUESTED", "USER",
                user.getId().toString(), null, ip);
    }

    /** Jetonu tüketip yeni şifreyi yazar ve <b>tüm oturumları kapatır</b>. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String ip) {
        VerificationToken token = consume(rawToken, VerificationPurpose.PASSWORD_RESET);
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(EmailVerificationService::invalidToken);

        user.changePassword(passwordEncoder.encode(newPassword));
        // Sıfırlama bağlantısı zaten posta kutusuna erişimi kanıtladı; adresi
        // doğrulanmamış bir hesap bu noktada doğrulanmış sayılabilir.
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
        }
        tokenService.revokeAllForUser(user.getId());
        auditRecorder.record(user.getId(), user.getUsername(), "PASSWORD_RESET", "USER",
                user.getId().toString(), null, ip);
        mailService.send(user.getEmail(), templates.passwordChanged(user.getDisplayName()));
    }

    // ------------------------------------------------------------ kapı

    /**
     * Doğrulanmamış e-postayla yapılamayacak işlemlerin başında çağrılır
     * (satın alma, stant başvurusu). İstemcinin düğmeyi gizlemesi güvenlik
     * sayılmaz — karar sunucuda verilir.
     */
    @Transactional(readOnly = true)
    public void assertVerified(UUID userId) {
        boolean verified = userRepository.findById(userId)
                .map(User::isEmailVerified)
                .orElse(false);
        if (!verified) {
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Bu işlem için e-posta adresini doğrulaman gerekiyor");
        }
    }

    // ------------------------------------------------------------ yardımcılar

    /**
     * Jetonu doğrular ve <b>kullanılmış</b> olarak işaretler.
     *
     * @param expected beklenen amaç; {@code null} ise amaç kontrolü çağırana bırakılır
     */
    private VerificationToken consume(String rawToken, VerificationPurpose expected) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        VerificationToken token = tokenRepository.findByTokenHash(hash(rawToken.trim()))
                .orElseThrow(EmailVerificationService::invalidToken);
        if (!token.isUsable(Instant.now())) {
            throw invalidToken();
        }
        if (expected != null && token.getPurpose() != expected) {
            throw invalidToken();
        }
        token.markUsed();
        return token;
    }

    private String issueToken(UUID userId, VerificationPurpose purpose, String targetEmail,
                              Duration ttl, String ip) {
        tokenRepository.invalidateOpenTokens(userId, purpose, Instant.now());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        // URL'de taşınacağı için base64url + dolgusuz.
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokenRepository.save(new VerificationToken(
                userId, hash(raw), purpose, targetEmail, Instant.now().plus(ttl), ip));
        return raw;
    }

    /** Kısa pencerede çok fazla bağlantı istenmesini engeller. */
    private void guardSendRate(UUID userId, VerificationPurpose purpose) {
        long recent = tokenRepository.countRecent(userId, purpose, Instant.now().minus(SEND_WINDOW));
        if (recent >= MAX_SENDS_PER_WINDOW) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Çok fazla istek gönderdiniz, birkaç dakika sonra tekrar deneyin");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.toLowerCase(Locale.ROOT).trim();
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 yok", ex);
        }
    }

    private static ApiException invalidToken() {
        // Tek mesaj: "yok" ile "süresi dolmuş" ayrımı saldırgana bilgi verir.
        return new ApiException(ErrorCode.VERIFICATION_TOKEN_INVALID,
                "Bağlantı geçersiz ya da süresi dolmuş. Yeni bir bağlantı isteyin.");
    }

    /** Süresi geçmiş jetonlar tabloyu şişirmesin — günde bir temizlenir. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = tokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
        if (removed > 0) {
            log.info("Süresi dolmuş {} doğrulama jetonu temizlendi", removed);
        }
    }
}
