package com.waydee.identity.api.dto;

import com.waydee.common.storage.MediaUrls;
import com.waydee.identity.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Kullanıcı adı zorunludur")
            @Pattern(regexp = "^[a-z0-9_]{3,30}$",
                    message = "Kullanıcı adı 3-30 karakter olmalı; küçük harf, rakam ve alt çizgi içerebilir")
            String username,

            @NotBlank(message = "E-posta zorunludur")
            @Email(message = "Geçerli bir e-posta girin")
            @Size(max = 255)
            String email,

            @NotBlank(message = "Şifre zorunludur")
            @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalı")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$",
                    message = "Şifre en az bir harf ve bir rakam içermeli")
            String password,

            @NotBlank(message = "Görünen ad zorunludur")
            @Size(min = 2, max = 60, message = "Görünen ad 2-60 karakter olmalı")
            String displayName,

            /** Cloudflare Turnstile widget'ının ürettiği tek kullanımlık jeton. */
            @Size(max = 2048) String turnstileToken
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Kullanıcı adı veya e-posta zorunludur")
            String usernameOrEmail,

            @NotBlank(message = "Şifre zorunludur")
            String password,

            /** Cloudflare Turnstile widget'ının ürettiği tek kullanımlık jeton. */
            @Size(max = 2048) String turnstileToken
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh token zorunludur")
            String refreshToken
    ) {
    }

    public record UserResponse(
            UUID id,
            String username,
            /**
             * 🔴 V47 — kullanıcı adı SİSTEM tarafından üretildi, kullanıcı henüz
             * kendi adını seçmedi (Google ile kayıt). Arayüz bu bayrağı görünce
             * yumuşak bir seçim ekranı açar.
             */
            boolean usernamePending,
            String email,
            String displayName,
            String bio,
            String avatarUrl,
            String role,
            boolean privateAccount,
            /** Arayüz doğrulama şeridini bu bayrağa göre gösterir. */
            boolean emailVerified,
            String themeMode,
            String mapStyle,
            /** Arayüz dili; null ise istemci tarayıcı dilini kullanır. */
            String locale,
            /** Üyelik planı (V33/V37): FREE | PRO | PREMIUM. Arayüz limitleri buna göre çizer. */
            String plan,
            /** Mavi tik — ücretli üyelikle gelir (bkz. {@code User#hasVerifiedBadge}). */
            boolean verified,
            /** Üyeliğin bitiş anı (süreli, V35); FREE hesapta {@code null}. */
            Instant planExpiresAt,
            /** Faturalama dönemi (V37): MONTHLY | YEARLY; FREE hesapta {@code null}. */
            String planPeriod,
            /** Haritada mağaza dairesi açabilir mi — yalnız yürürlükteki PREMIUM. */
            boolean canOwnStore,
            /**
             * <b>Ücretsiz 1 aylık mağaza denemesi hâlâ duruyor mu</b> (18 Ağu 2026).
             *
             * <p>🔴 {@link #canOwnStore} ile <b>karıştırılmamalı</b>: biri
             * "planı buna izin veriyor", diğeri "harcanmamış bir deneme hakkı
             * var". Ücretsiz kullanıcıda birincisi {@code false} iken ikincisi
             * {@code true} olur ve arayüzün <b>daveti</b> ikincisine bakar —
             * birincisine baksaydı davet hiç kimseye çıkmazdı.
             */
            boolean storeTrialAvailable,
            /**
             * <b>Kayıt sonrası kategori popup'ı çıkmalı mı</b> (V52).
             *
             * <p>🔴 İstemci bunu {@code storeCategoryId == null} diye
             * <b>türetemez</b>: *"geç"* diyen kullanıcıda o alan da boştur ve
             * popup her açılışta yeniden çıkardı. Sorulmuş olmak ile
             * cevaplanmış olmak farklı iki olaydır ve ayrımı sunucu tutar.
             */
            boolean needsStoreCategory,
            /** Kullanıcının seçtiği mağaza alanı (V52); ayarlar ekranı bunu işaretli gösterir. */
            UUID storeCategoryId,
            Instant createdAt
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.isUsernamePending(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getBio(),
                    MediaUrls.of(user.getAvatarMediaId()),
                    user.getRole().name(),
                    user.isPrivateAccount(),
                    user.isEmailVerified(),
                    user.getThemeMode(),
                    user.getMapStyle(),
                    user.getLocale(),
                    // 🔴 `getPlan()` DEĞİL: süresi dolmuş PRO satırı hâlâ PRO
                    // yazar; arayüz sınırları yürürlükteki plana göre çizmeli.
                    user.effectivePlan().name(),
                    user.hasVerifiedBadge(),
                    user.isPlanActive() ? user.getPlanExpiresAt() : null,
                    user.isPlanActive() && user.getPlanPeriod() != null
                            ? user.getPlanPeriod().name() : null,
                    user.canOwnStore(),
                    user.freeStoreTrialAvailable(),
                    user.needsStoreCategoryPrompt(),
                    user.getStoreCategoryId(),
                    user.getCreatedAt());
        }
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UserResponse user
    ) {
    }

    /** Başka bir kullanıcının herkese açık profili (e-posta içermez). */
    public record PublicUserResponse(
            UUID id,
            String username,
            String displayName,
            String bio,
            String avatarUrl,
            boolean privateAccount,
            /** Mavi tik — PRO üyelikle gelir. Plan adının kendisi SIZDIRILMAZ. */
            boolean verified,
            Instant createdAt
    ) {
        public static PublicUserResponse from(User user) {
            return new PublicUserResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getBio(),
                    MediaUrls.of(user.getAvatarMediaId()),
                    user.isPrivateAccount(),
                    user.hasVerifiedBadge(),
                    user.getCreatedAt());
        }
    }

    public record UpdateMeRequest(
            @Size(min = 2, max = 60, message = "Görünen ad 2-60 karakter olmalı")
            String displayName,

            @Size(max = 280, message = "Bio en fazla 280 karakter olabilir")
            String bio,

            UUID avatarMediaId,

            Boolean privateAccount,

            @Pattern(regexp = "light|dark", message = "Tema modu 'light' ya da 'dark' olmalı")
            String themeMode,

            @Pattern(regexp = "light|dark|streets|satellite", message = "Geçersiz harita stili")
            String mapStyle,

            /** Desteklenen diller; boş bırakılırsa tarayıcı dili takip edilir. */
            @Pattern(regexp = "tr|en|ar|de|es", message = "Desteklenmeyen dil")
            String locale
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Mevcut şifre zorunludur") String currentPassword,

            @NotBlank(message = "Yeni şifre zorunludur")
            @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalı")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Şifre en az bir harf ve bir rakam içermeli")
            String newPassword
    ) {
    }

    public record ChangeEmailRequest(
            @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin") @Size(max = 255)
            String newEmail,

            @NotBlank(message = "Mevcut şifre zorunludur") String currentPassword
    ) {
    }

    // ------------------------------------------------------- e-posta doğrulama

    /**
     * E-posta değişimi artık <b>anında uygulanmaz</b>: yeni adrese bağlantı gider,
     * tıklanana kadar hesap eski adresle çalışmaya devam eder.
     */
    public record EmailChangeResponse(boolean pendingVerification, String pendingEmail) {
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "Doğrulama kodu zorunludur") @Size(max = 200) String token
    ) {
    }

    /** Doğrulama sonucu — istemci {@code purpose}'a göre mesajını seçer. */
    public record VerificationResultResponse(String purpose, String email) {
    }

    public record EmailOnlyRequest(
            @NotBlank(message = "E-posta zorunludur") @Email(message = "Geçerli bir e-posta girin") @Size(max = 255)
            String email,

            /** Cloudflare Turnstile jetonu — bu uçlar da posta gönderdiği için korunur. */
            @Size(max = 2048) String turnstileToken
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Doğrulama kodu zorunludur") @Size(max = 200) String token,

            @NotBlank(message = "Yeni şifre zorunludur")
            @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalı")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "Şifre en az bir harf ve bir rakam içermeli")
            String newPassword
    ) {
    }
}
