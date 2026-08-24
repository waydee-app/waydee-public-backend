package com.waydee.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waydee.common.error.ApiError;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.net.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Redis destekli sabit pencereli hız sınırlayıcı.
 * Auth uçları dar (dakikada 15), diğer API uçları geniş (dakikada 300) limitlidir.
 * Redis erişilemezse istekleri engellemez (fail-open) — kullanılabilirlik önceliklidir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT_PER_MINUTE = 15;
    private static final int API_LIMIT_PER_MINUTE = 300;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    /**
     * 🔴 17 Ağu 2026 — kovanın anahtarı artık {@link ClientIpResolver}'dan gelir.
     * Eskiden en soldaki {@code X-Forwarded-For} girdisi okunuyordu ve o girdiyi
     * <b>istemcinin kendisi</b> yazabildiği için hız sınırı tamamen atlanabiliyordu
     * (gerekçe ve ölçüm: {@link ClientIpResolver}).
     */
    private final ClientIpResolver clientIpResolver;

    @Value("${waydee.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Vitrin kapısı da kimlik yüzeyi sayılır: geçiş üretimi dar kovada olmalı,
        // yoksa bot sürekli yeni ziyaretçi geçişi isteyebilir.
        String uri = request.getRequestURI();
        boolean isAuthEndpoint = uri.startsWith("/api/v1/auth/") || uri.equals("/api/v1/public/verify");
        int limit = isAuthEndpoint ? AUTH_LIMIT_PER_MINUTE : API_LIMIT_PER_MINUTE;
        String bucket = isAuthEndpoint ? "auth" : "api";
        String key = "rl:%s:%s:%d".formatted(bucket, clientIp(request), System.currentTimeMillis() / 60_000);

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(70));
            }
            if (count != null && count > limit) {
                reject(request, response);
                return;
            }
        } catch (Exception ex) {
            log.warn("Rate limit deposuna erişilemedi, istek engellenmedi: {}", ex.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMITED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(ErrorCode.RATE_LIMITED,
                "Çok fazla istek gönderildi, lütfen biraz bekleyin", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
