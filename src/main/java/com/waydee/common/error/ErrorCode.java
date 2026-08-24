package com.waydee.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    /** Doğrulama/sıfırlama bağlantısı geçersiz, kullanılmış ya da süresi dolmuş. */
    VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    /** İşlem doğrulanmış e-posta ister — istemci "tekrar gönder" akışını açar. */
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    /**
     * Plan kotası doldu (V33). ⚠️ Genel {@code FORBIDDEN}'dan AYRI: istemci
     * bunu "yasak" diye göstermeyip <b>Pro'ya yükselt</b> ekranını açar.
     */
    PLAN_LIMIT_REACHED(HttpStatus.FORBIDDEN),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** Vitrin geçişi yok/süresi dolmuş → istemci doğrulama kapısını yeniden açar. */
    VISITOR_PASS_REQUIRED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    USERNAME_TAKEN(HttpStatus.CONFLICT),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    REGION_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    TERRITORY_OVERLAP(HttpStatus.CONFLICT),
    ZONE_BOUNDARY_CROSSED(HttpStatus.CONFLICT),
    RADIUS_OUT_OF_BOUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED),
    /**
     * Yapay zekâ kredisi yetmiyor (V45).
     *
     * <p>⚠️ {@link #PLAN_LIMIT_REACHED}'ten <b>ayrı</b>: orada çözüm "plana geç",
     * burada "bu dönemin kredisi bitti" — arayüz iki farklı ekran açar ve ikisini
     * karıştırmak, kredisi biten Premium üyeye "Premium'a geç" demek olurdu.
     */
    INSUFFICIENT_CREDITS(HttpStatus.PAYMENT_REQUIRED),
    UNSUPPORTED_MEDIA(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
