package com.waydee.identity.application;

import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.identity.api.dto.AuthDtos.ChangeEmailRequest;
import com.waydee.identity.api.dto.AuthDtos.ChangePasswordRequest;
import com.waydee.identity.api.dto.AuthDtos.EmailChangeResponse;
import com.waydee.identity.api.dto.AuthDtos.UpdateMeRequest;
import com.waydee.identity.api.dto.AuthDtos.UserResponse;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.application.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.waydee.identity.domain.UsernamePolicy;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Kullanıcı adı değiştirme bekleme süresi (gün).
     * <p>Profil adresi kullanıcı adıdır; sık değişim paylaşılmış bağlantıları
     * ve arama motoru kayıtlarını kırar.
     */
    private static final int CHANGE_COOLDOWN_DAYS = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final MediaService mediaService;
    private final EmailVerificationService emailVerificationService;
    private final com.waydee.moderation.application.RestrictionService restrictionService;
    /** Ad değişikliği denetim kaydına düşer — profil adresi değişen bir olaydır. */
    private final com.waydee.common.audit.AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    /**
     * Başka bir kullanıcının herkese açık kartı — {@code /u/:id} köprüsünün
     * kimliği kullanıcı adına çevirdiği yer.
     *
     * <p>🔴 16 Ağu 2026 — <b>yönetim hesapları 404.</b> Burada
     * {@code requireUser} çağrılıyordu ve o eleme yapmaz (oturum sahibinin
     * kendi profilini de o getirir). Oturum açmış herhangi biri
     * {@code /u/<adminId>} ile yöneticinin kullanıcı adını öğrenip vitrine
     * gidebiliyordu; tag kapısını kapatıp burayı açık bırakmak kapıyı hiç
     * kapatmamaktı.
     *
     * <p>⚠️ Eleme {@code requireUser}'a KONULMAZ — o metodu {@code getMe} ve
     * {@code updateMe} de kullanır; yönetici kendi hesabını yönetemez olurdu.
     */
    @Transactional(readOnly = true)
    public com.waydee.identity.api.dto.AuthDtos.PublicUserResponse getPublic(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(User::hasPublicProfile)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        return com.waydee.identity.api.dto.AuthDtos.PublicUserResponse.from(user);
    }

    /**
     * <b>Kullanıcı adı değiştirme</b> (V47, 17 Ağu 2026).
     *
     * <h3>Kurallar ve gerekçeleri</h3>
     * <ol>
     *   <li><b>Biçim + rezerve</b> tek kaynaktan ({@link UsernamePolicy}).
     *       Rezerve liste olmadan biri {@code settings} adını alabilir ve
     *       profili kalıcı olarak erişilemez olurdu ({@code /:tag} rotası
     *       statik rotaların ARDINDAN gelir).</li>
     *   <li><b>Benzersizlik</b> DB'de de tekil kısıtlıdır; buradaki kontrol
     *       kullanıcıya anlaşılır hata vermek içindir.</li>
     *   <li><b>Bekleme süresi {@value #CHANGE_COOLDOWN_DAYS} gün</b> — ama
     *       <b>ilk değişiklik serbest</b>. Profil adresi kullanıcı adıdır;
     *       her değişiklik paylaşılmış bağlantıları ve arama motoru
     *       kayıtlarını kırar. Google'ın ürettiği adı düzeltmek için 30 gün
     *       beklemek ise saçma olurdu — bu yüzden {@code usernameChangedAt}
     *       null iken kapı açıktır.</li>
     * </ol>
     *
     * <p>⚠️ Aynı ada geçiş (yalnız büyük/küçük harf farkı) <b>hata değildir</b>
     * ama bekleme süresini de tüketmemeli; sessizce başarıyla döner.
     */
    @Transactional
    public UserResponse changeUsername(UUID userId, String rawUsername) {
        User user = requireUser(userId);
        String next = UsernamePolicy.normalize(rawUsername);

        if (next.equals(user.getUsername())) {
            // Değişiklik yok; yalnız "bekliyor" durumunu kapat (üretilen adı kabul etti).
            if (user.isUsernamePending()) {
                user.keepGeneratedUsername();
            }
            return UserResponse.from(user);
        }
        if (!UsernamePolicy.hasValidFormat(next)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Kullanıcı adı 3-30 karakter olmalı; küçük harf, rakam ve alt çizgi içerebilir");
        }
        if (UsernamePolicy.isReserved(next)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Bu kullanıcı adı kullanılamaz");
        }
        if (userRepository.existsByUsername(next)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN, "Bu kullanıcı adı zaten alınmış");
        }
        assertChangeAllowed(user);

        String previous = user.getUsername();
        user.changeUsername(next);
        auditRecorder.record(user.getId(), next, "USERNAME_CHANGED", "USER",
                user.getId().toString(), java.util.Map.of("from", previous, "to", next), null);
        return UserResponse.from(user);
    }

    /** Bekleme süresi — ilk değişiklikte ve "ad bekliyor" durumunda uygulanmaz. */
    private void assertChangeAllowed(User user) {
        if (user.isUsernamePending() || user.getUsernameChangedAt() == null) {
            return;
        }
        Instant next = user.getUsernameChangedAt().plus(Duration.ofDays(CHANGE_COOLDOWN_DAYS));
        if (Instant.now().isBefore(next)) {
            long daysLeft = Math.max(1, Duration.between(Instant.now(), next).toDays());
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Kullanıcı adını " + CHANGE_COOLDOWN_DAYS + " günde bir değiştirebilirsin. "
                            + "Kalan: " + daysLeft + " gün.");
        }
    }

    /**
     * Ad seçilebilir mi — seçim ekranındaki canlı kontrol.
     *
     * <p>⚠️ Bu uç bir <b>kullanıcı adı sayaç aracı</b> değildir: adlar zaten
     * herkese açık ({@code waydee.com/{username}}), yani "alınmış mı"
     * bilgisi zaten dışarıdan görülebilir. Yine de hız sınırı API kovasında.
     */
    @Transactional(readOnly = true)
    public UsernameAvailability checkUsername(UUID userId, String rawUsername) {
        String next = UsernamePolicy.normalize(rawUsername);
        if (!UsernamePolicy.hasValidFormat(next)) {
            return new UsernameAvailability(next, false, "FORMAT");
        }
        if (UsernamePolicy.isReserved(next)) {
            return new UsernameAvailability(next, false, "RESERVED");
        }
        // Kendi mevcut adı "alınmış" sayılmamalı.
        User me = userRepository.findById(userId).orElse(null);
        if (me != null && next.equals(me.getUsername())) {
            return new UsernameAvailability(next, true, null);
        }
        if (userRepository.existsByUsername(next)) {
            return new UsernameAvailability(next, false, "TAKEN");
        }
        return new UsernameAvailability(next, true, null);
    }

    /** @param reason FORMAT | RESERVED | TAKEN — arayüz mesajı buna göre seçer (çeviri istemcide). */
    public record UsernameAvailability(String username, boolean available, String reason) {
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateMeRequest request) {
        // Görünüm tercihleri (tema/harita) kısıtlamadan muaf; profil alanları değişiyorsa kontrol et.
        boolean touchesProfile = request.displayName() != null || request.bio() != null
                || request.avatarMediaId() != null || request.privateAccount() != null;
        if (touchesProfile) {
            restrictionService.assertAllowed(userId, com.waydee.moderation.domain.RestrictedAction.PROFILE_EDIT);
        }
        User user = requireUser(userId);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        if (request.avatarMediaId() != null) {
            // IDOR koruması: yalnız kendi yüklediğin medya profil fotoğrafı olabilir.
            mediaService.assertOwnedBy(request.avatarMediaId(), userId);
            user.setAvatarMediaId(request.avatarMediaId());
        }
        if (request.privateAccount() != null) {
            user.setPrivateAccount(request.privateAccount());
        }
        if (request.themeMode() != null) {
            user.setThemeMode(request.themeMode());
        }
        if (request.locale() != null) {
            user.setLocale(request.locale());
        }
        /*
         * 🔴 Harita stili TEMİZLENEBİLİR olmalı (10 Ağu 2026).
         *
         * Bu uçta `null` "alan gönderilmedi" demektir (kısmi güncelleme kalıbı),
         * dolayısıyla `null` ile tercihi silmek mümkün değildi. Ayarlar'daki
         * <b>"Temayı takip et"</b> düğmesi bu yüzden tercihi silemiyor,
         * çaresizlikten o anki temayı (`light`/`dark`) <b>sabit bir seçim</b>
         * olarak yazıyordu — kullanıcı bir sonraki girişinde taban temaya
         * kilitlenmiş oluyor ve tema düğmesi haritayı değiştirmiyordu.
         * <b>Boş dize</b> artık "tercihimi sil" anlamına gelir.
         */
        if (request.mapStyle() != null) {
            user.setMapStyle(request.mapStyle().isBlank() ? null : request.mapStyle());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        // ⚠️ Google ile açılan hesabın mevcut şifresi YOKTUR — "mevcut şifreni
        // yaz" kapısı burada anlamsızdır ve null hash ile karşılaştırma yapılamaz.
        // Şifre eklemek isteyen kullanıcının yolu "şifremi unuttum"dur: adresine
        // gönderilen bağlantı adresin sahipliğini kanıtlar ve şifreyi belirler.
        if (!user.hasPassword()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Bu hesap Google ile açıldı ve şifresi yok. Şifre belirlemek için "
                            + "\"Şifremi unuttum\" akışını kullan.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Mevcut şifre hatalı");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        // Diğer tüm oturumları kapat (güvenlik).
        tokenService.revokeAllForUser(userId);
    }

    /**
     * E-posta değişimi <b>artık anında uygulanmaz</b>: yeni adrese doğrulama
     * bağlantısı gider ve adres ancak tıklanınca hesaba yazılır. Yanlış yazılan
     * bir adres böylece hesabı erişilemez hâle getiremez.
     */
    @Transactional
    public EmailChangeResponse changeEmail(UUID userId, ChangeEmailRequest request, String ip) {
        String pending = emailVerificationService.requestEmailChange(
                userId, request.newEmail(), request.currentPassword(), ip);
        return new EmailChangeResponse(true, pending);
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
    }
}
