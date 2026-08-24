package com.waydee.identity.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.identity.api.dto.AuthDtos.ChangeEmailRequest;
import com.waydee.identity.api.dto.AuthDtos.ChangePasswordRequest;
import com.waydee.identity.api.dto.AuthDtos.EmailChangeResponse;
import com.waydee.identity.api.dto.AuthDtos.PublicUserResponse;
import com.waydee.identity.api.dto.AuthDtos.UpdateMeRequest;
import com.waydee.identity.api.dto.AuthDtos.UserResponse;
import com.waydee.identity.api.dto.BlockDtos.BlockRequest;
import com.waydee.identity.api.dto.BlockDtos.BlockedUserRow;
import com.waydee.identity.api.dto.SocialLinkDtos.SocialLinkView;
import com.waydee.identity.api.dto.SocialLinkDtos.UpdateSocialLinksRequest;
import com.waydee.identity.application.BlockService;
import com.waydee.identity.application.EmailVerificationService;
import com.waydee.identity.application.SocialLinkService;
import com.waydee.identity.application.UserService;
import com.waydee.traffic.application.ClientInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Users", description = "Kullanıcı profili")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SocialLinkService socialLinkService;
    private final BlockService blockService;
    private final EmailVerificationService emailVerificationService;
    private final ClientInfo clientInfo;

    @Operation(summary = "Oturum açan kullanıcının profili")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getMe(principal.id());
    }

    @Operation(summary = "Başka bir kullanıcının herkese açık profili")
    @GetMapping("/{id}")
    public PublicUserResponse getById(@PathVariable UUID id) {
        return userService.getPublic(id);
    }

    /**
     * <b>Kullanıcı adını değiştir</b> (V47, 17 Ağu 2026).
     *
     * <p>Google ile açılan hesapların adı e-posta yerel kısmından türetiliyor
     * ve kullanıcının seçimi değil. Arayüz ilk girişte {@code usernamePending}
     * bayrağını görüp seçim ekranını açar; buradan da her zaman değiştirilebilir.
     *
     * <p>⚠️ {@code PATCH /users/me} içine KONMADI: ad değişikliği profil
     * adresini değiştirir, bekleme süresine tabidir ve denetim kaydı üretir —
     * bio güncellemesiyle aynı uçta olmamalı.
     */
    @Operation(summary = "Kullanıcı adını değiştir")
    @PatchMapping("/me/username")
    public UserResponse changeUsername(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody ChangeUsernameRequest request) {
        return userService.changeUsername(principal.id(), request.username());
    }

    /** Seçim ekranındaki canlı "bu ad uygun mu" kontrolü. */
    @Operation(summary = "Kullanıcı adı uygun mu")
    @GetMapping("/me/username-available")
    public UserService.UsernameAvailability usernameAvailable(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("u") String username) {
        return userService.checkUsername(principal.id(), username);
    }

    @Operation(summary = "Profil güncelleme (ad/bio/avatar/gizlilik)")
    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody UpdateMeRequest request) {
        return userService.updateMe(principal.id(), request);
    }

    @Operation(summary = "Şifre değiştir (diğer oturumları kapatır)")
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.id(), request);
    }

    @Operation(summary = "E-posta değiştir — yeni adrese doğrulama bağlantısı gönderir")
    @PostMapping("/me/email")
    public EmailChangeResponse changeEmail(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @Valid @RequestBody ChangeEmailRequest request,
                                           HttpServletRequest http) {
        return userService.changeEmail(principal.id(), request, clientInfo.ip(http));
    }

    @Operation(summary = "Kendi adresime doğrulama bağlantısını tekrar gönder")
    @PostMapping("/me/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendMyVerification(@AuthenticationPrincipal AuthenticatedUser principal,
                                     HttpServletRequest http) {
        emailVerificationService.resendVerification(
                userService.getMe(principal.id()).email(), clientInfo.ip(http));
    }

    @Operation(summary = "Kendi sosyal medya bağlantılarım")
    @GetMapping("/me/social-links")
    public List<SocialLinkView> mySocialLinks(@AuthenticationPrincipal AuthenticatedUser principal) {
        return socialLinkService.list(principal.id());
    }

    @Operation(summary = "Sosyal medya bağlantılarını güncelle (tam değiştirme)")
    @PutMapping("/me/social-links")
    public List<SocialLinkView> updateSocialLinks(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @Valid @RequestBody UpdateSocialLinksRequest request) {
        return socialLinkService.replace(principal.id(), request.links());
    }

    @Operation(summary = "Bir kullanıcının herkese açık sosyal medya bağlantıları")
    @GetMapping("/{id}/social-links")
    public List<SocialLinkView> socialLinksOf(@PathVariable UUID id) {
        return socialLinkService.list(id);
    }

    // ------------------------------------------------------------ engelleme

    @Operation(summary = "Engellediğim kullanıcılar")
    @GetMapping("/me/blocks")
    public List<BlockedUserRow> myBlocks(@AuthenticationPrincipal AuthenticatedUser principal) {
        return blockService.myBlocks(principal.id());
    }

    @Operation(summary = "Kullanıcıyı engelle (karşılıklı takipler düşer)")
    @PostMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable UUID id,
                      @AuthenticationPrincipal AuthenticatedUser principal,
                      @Valid @RequestBody(required = false) BlockRequest request) {
        blockService.block(principal.id(), id, request != null ? request.reason() : null);
    }

    @Operation(summary = "Engeli kaldır")
    @DeleteMapping("/{id}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        blockService.unblock(principal.id(), id);
    }

    /** ⚠️ Biçim kuralı {@code UsernamePolicy.FORMAT} ile birebir aynı olmalı. */
    public record ChangeUsernameRequest(
            @jakarta.validation.constraints.NotBlank(message = "Kullanıcı adı zorunludur")
            @jakarta.validation.constraints.Pattern(
                    regexp = "^[A-Za-z0-9_]{3,30}$",
                    message = "Kullanıcı adı 3-30 karakter olmalı; harf, rakam ve alt çizgi içerebilir")
            String username) {
    }
}
