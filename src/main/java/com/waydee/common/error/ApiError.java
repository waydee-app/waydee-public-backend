package com.waydee.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * @param requestId <b>İsteğin korelasyon kimliği</b> (20 Ağu 2026).
 *
 *        <p>🔴 Hata yanıtında dönmesinin tek bir sebebi var ve çok değerli:
 *        kullanıcı <i>"hata aldım"</i> dediğinde elimizde <b>tek bir kimlik</b>
 *        oluyor ve o kimlikle isteğin <b>tüm</b> log satırlarını çekebiliyoruz.
 *        Öncesinde destek talebi "ne zaman, hangi ekranda?" diye başlayan bir
 *        tahmin oyunuydu.
 *
 *        <p>⚠️ Sızıntı değildir: rastgele bir UUID'dir, hiçbir iç yapıyı
 *        anlatmaz. Aynı değer {@code X-Request-Id} başlığında da döner —
 *        gövdeye konmasının sebebi, kullanıcının ekranda görüp
 *        <b>söyleyebilmesi</b>.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors,
        String requestId
) {

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), code.status().value(), code.name(), message, path,
                null, currentRequestId());
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), ErrorCode.VALIDATION_ERROR.status().value(),
                ErrorCode.VALIDATION_ERROR.name(), message, path, fieldErrors, currentRequestId());
    }

    /**
     * MDC'deki korelasyon kimliği.
     *
     * <p>⚠️ {@code null} olabilir ve olmalı: kimliği {@code RequestLogFilter}
     * koyar; filtre zincirinin dışında üretilen bir hatada (ör. açılışta)
     * MDC boştur. {@code NON_NULL} sayesinde alan yanıta hiç girmez —
     * boş dize göndermek, "kimlik var ama boş" gibi okunurdu.
     */
    private static String currentRequestId() {
        return org.slf4j.MDC.get("requestId");
    }
}
