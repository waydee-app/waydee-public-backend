package com.waydee.common.ratelimit;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Hesap bazlı brute-force koruması — IP bazlı hız sınırından BAĞIMSIZDIR
 * (X-Forwarded-For sahteciliği bunu atlatamaz). Aynı hesaba art arda
 * başarısız giriş denemesi kilit penceresi boyunca engellenir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    /** Kilitliyse isteği reddeder. Redis erişilemezse engellemez (fail-open, IP limiti hâlâ devrede). */
    public void check(String identifier) {
        try {
            String value = redis.opsForValue().get(key(identifier));
            if (value != null && Long.parseLong(value) >= MAX_FAILURES) {
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        "Çok fazla başarısız deneme — lütfen daha sonra tekrar deneyin");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Login kilidi okunamadı, deneme engellenmedi: {}", ex.getMessage());
        }
    }

    public void onFailure(String identifier) {
        try {
            Long count = redis.opsForValue().increment(key(identifier));
            if (count != null && count == 1L) {
                redis.expire(key(identifier), WINDOW);
            }
        } catch (Exception ex) {
            log.warn("Login kilidi yazılamadı: {}", ex.getMessage());
        }
    }

    public void onSuccess(String identifier) {
        try {
            redis.delete(key(identifier));
        } catch (Exception ex) {
            log.debug("Login kilidi temizlenemedi: {}", ex.getMessage());
        }
    }

    private String key(String identifier) {
        return "login:fail:" + identifier.toLowerCase(Locale.ROOT).trim();
    }
}
