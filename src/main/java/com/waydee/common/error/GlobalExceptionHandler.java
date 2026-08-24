package com.waydee.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * <b>İş kuralı reddi</b> (4xx).
     *
     * <h3>🔴 17 Ağu 2026 — BU LOG SATIRI YOKTU VE TEŞHİSİ İMKÂNSIZ KILIYORDU</h3>
     * Kullanıcı "gönderi oluşturulamadı" diyordu; sunucu tarafında ise
     * <b>hiçbir iz yoktu</b>: ApiException sessizce 4xx'e çevriliyordu.
     * CloudWatch'ta ölçüldü — <b>113 adet 4xx</b> vardı ama hangisi, hangi uçta
     * ve neden olduğu <b>hiçbir yerde yazmıyordu</b>. Yarım gün, logda olmayan
     * bir hatayı dolaylı kanıtla aramakla geçti.
     *
     * <p>⚠️ Seviye <b>WARN</b>: 4xx bir sunucu arızası değil, beklenen bir
     * reddir (kota, yetki, doğrulama). ERROR yazmak gerçek arızaları gürültüye
     * gömerdi.
     * <p>⚠️ İstek GÖVDESİ loglanmaz — kişisel veri ve sır taşıyabilir. Yalnız
     * yöntem, yol, hata kodu, mesaj ve kullanıcı kimliği yazılır; teşhis için
     * yeten ve sızdırmayan küme budur.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        log.warn("Reddedildi: {} {} -> {} ({}) [user={}]",
                req.getMethod(), req.getRequestURI(), ex.getCode(), ex.getMessage(), currentUserId());
        return respond(ex.getCode(), ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ApiError body = ApiError.validation("Doğrulama hatası", req.getRequestURI(), fields);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        return respond(ErrorCode.VALIDATION_ERROR, ex.getMessage(), req);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest req) {
        return respond(ErrorCode.MALFORMED_REQUEST, "İstek gövdesi okunamadı", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return respond(ErrorCode.UNAUTHORIZED, "Kimlik doğrulaması gerekli", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(ErrorCode.FORBIDDEN, "Bu işlem için yetkiniz yok", req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return respond(ErrorCode.PAYLOAD_TOO_LARGE, "Dosya boyutu limiti aşıldı", req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return respond(ErrorCode.CONFLICT, "Kayıt eşzamanlı olarak değiştirildi, lütfen tekrar deneyin", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Veri bütünlüğü ihlali: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.CONFLICT, "İstek mevcut verilerle çakışıyor", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return respond(ErrorCode.NOT_FOUND, "Kaynak bulunamadı", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Beklenmeyen hata: {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Beklenmeyen bir hata oluştu", req);
    }

    /** Oturumdaki kullanıcı — yoksa "anon". Teşhis için şart: hangi hesap reddedildi. */
    private static String currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        return principal instanceof com.waydee.common.security.AuthenticatedUser u
                ? u.id().toString()
                : "anon";
    }

    private ResponseEntity<ApiError> respond(ErrorCode code, String message, HttpServletRequest req) {
        return ResponseEntity.status(code.status()).body(ApiError.of(code, message, req.getRequestURI()));
    }
}
