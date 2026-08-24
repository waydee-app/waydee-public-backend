package com.waydee.admin.application;

import com.waydee.admin.api.dto.AdminDtos.AdminUserResponse;
import com.waydee.admin.api.dto.AdminDtos.AuditLogResponse;
import com.waydee.admin.api.dto.AdminDtos.DashboardResponse;
import com.waydee.admin.api.dto.AdminDtos.DeleteUserRequest;
import com.waydee.admin.api.dto.AdminDtos.RecentPurchase;
import com.waydee.admin.api.dto.AdminDtos.UpdateUserRequest;
import com.waydee.common.audit.AuditLogRepository;
import com.waydee.common.audit.AuditRecorder;
import com.waydee.common.error.ApiException;
import com.waydee.common.error.ErrorCode;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.identity.domain.Role;
import com.waydee.identity.domain.User;
import com.waydee.identity.domain.UserPlan;
import com.waydee.identity.domain.UserStatus;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.infrastructure.PostRepository;
import com.waydee.territory.domain.TerritoryStatus;
import com.waydee.territory.infrastructure.PurchaseRepository;
import com.waydee.territory.infrastructure.TerritoryRepository;
import com.waydee.identity.infrastructure.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TerritoryRepository territoryRepository;
    private final PurchaseRepository purchaseRepository;
    private final PostRepository postRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditRecorder auditRecorder;

    /** Mali kayıt sayımı için — bkz. {@code countFinancialRows}. */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    /** V45 — elle plan değişiminde yapay zekâ kredisini de yüklemek için. */
    private final com.waydee.identity.application.CreditService creditService;

    // ------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = query != null && !query.isBlank()
                ? userRepository.search(query.trim(), pageable)
                : userRepository.findAll(pageable);
        return PageResponse.from(result, AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse updateUser(UUID userId, UpdateUserRequest request, AuthenticatedUser actor) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));
        if (user.getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.CONFLICT, "Kendi hesabınızı buradan değiştiremezsiniz");
        }

        /* 🔴 GÜVENLİK: ADMIN'e yükseltme ŞİFRE İSTER (9 Ağu 2026 denetimi).
           Denetimde bulundu: herhangi bir yönetici, tek bir PATCH ile istediği
           hesabı ADMIN yapabiliyordu. Açık kalmış ya da ele geçirilmiş bir
           yönetim oturumu, saldırganın kendi hesabını kalıcı yönetici yapması
           için yeterliydi — oturum kapansa bile erişim kalırdı.
           Artık yetki YÜKSELTME, silme ile aynı kapıdan geçer: yöneticinin
           kendi şifresi. Yetki DÜŞÜRME şifre istemez; kilitlenmeyi önlemek
           tehlikeyi artırmaz. */
        if (request.role() != null) {
            Role nextRole = Role.valueOf(request.role());
            if (nextRole == Role.ADMIN && user.getRole() != Role.ADMIN) {
                if (request.password() == null || request.password().isBlank()) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "Yönetici yetkisi vermek için şifrenizi girin");
                }
                User self = userRepository.findById(actor.id())
                        .orElseThrow(() -> ApiException.notFound("Yönetici bulunamadı"));
                if (!passwordEncoder.matches(request.password(), self.getPasswordHash())) {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR, "Şifreniz hatalı");
                }
            }
            user.setRole(nextRole);
        }
        if (request.status() != null) {
            user.setStatus(UserStatus.valueOf(request.status()));
            if (user.getStatus() == UserStatus.SUSPENDED) {
                refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            }
        }
        /* 🔴 Elle plan değişimi (destek / deneme / iade). Ödeme akışından
           bağımsızdır ama AYNI kuralı uygular.
           ⚠️ Ücretli planda `changePlan` DEĞİL `grantPlan`: üyelik sürelidir
           (V35) ve `changePlan` bitiş tarihini BOŞ bırakır. Boş bitişli bir PRO
           satırı `isPlanActive()` tarafından geçersiz sayıldığı için kullanıcı
           "PRO yapıldı" görünüp aslında FREE kalıyordu — ölçülerek yakalandı. */
        if (request.plan() != null) {
            UserPlan next = UserPlan.valueOf(request.plan());
            var period = com.waydee.identity.domain.BillingPeriod.ofNullable(request.planPeriod());
            user.grantPlan(next, period);
            /* 🔴 V45 — plan verilince yapay zekâ kredisi de yüklenir.
               ⚠️ Bunu atlamak, yöneticinin elle Premium yaptığı bir hesabı
               KREDİSİZ bırakırdı: kullanıcı Premium görünür ama stüdyoda tek
               görsel bile üretemezdi. Tekrar koruması `grantForPlan` içinde
               (anahtar = üyelik bitiş anı), yani aynı dönem iki kez yüklenmez. */
            if (next.paid()) {
                creditService.grantForPlan(user.getId(), next, period, user.getPlanExpiresAt());
            }
        }
        auditRecorder.record(actor.id(), actor.username(), "USER_UPDATED_BY_ADMIN", "USER",
                userId.toString(),
                Map.of("role", user.getRole().name(), "status", user.getStatus().name(),
                        "plan", user.effectivePlan().name()), null);
        return AdminUserResponse.from(user);
    }

    /**
     * <b>Hesabi sil</b> (yonetici).
     *
     * <p>KIRMIZI: bu bir <b>ANONIMLESTIRME</b>dir, satirin veritabanindan
     * silinmesi degil. Sebep olculdu: `users` satirina RESTRICT ile bagli on
     * tablo var - `invoices`, `purchases`, `posts`, `territories`,
     * `post_comments`, `messages`, `user_reports`... Gercek DELETE ya yabanci
     * anahtar hatasi verir ya da CASCADE eklenirse <b>FATURA ve SATIN ALMA
     * kayitlarini yok eder</b>. Mali kayit silinemez; kisisel veri silinir.
     *
     * <p>Yapilanlar: kimlik bilgileri temizlenir (e-posta, ad, biyografi,
     * avatar), oturumlar iptal edilir, sifre kullanilamaz hale getirilir,
     * gonderiler yumusak silinir, magaza/bolgeler geri alinir, sosyal
     * baglantilar ve profil baglantilari silinir. Kalan: fatura/satin alma
     * satirlari ve denetim kaydi.
     *
     * <p>UYARI: geri donusu YOKTUR. Bu yuzden cagri iki katli dogrulama
     * ister (bkz. {@code DeleteUserRequest}).
     */
    @Transactional
    public void deleteUser(UUID userId, DeleteUserRequest request, AuthenticatedUser actor) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        if (target.getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.CONFLICT, "Kendi hesabınızı silemezsiniz");
        }
        assertNotLastActiveAdmin(target);
        if (!target.getUsername().equalsIgnoreCase(request.confirmUsername().trim())) {
            throw ApiException.badRequest("Kullanıcı adı eşleşmiyor — silme iptal edildi");
        }

        /* Yeniden kimlik dogrulama: yoneticinin KENDI sifresi. */
        User admin = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Yönetici bulunamadı"));
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Şifreniz hatalı");
        }

        String username = target.getUsername();
        String email = target.getEmail();

        /* 🔴 SIRA ÖNEMLİ — ölçülerek yakalandı.
           Önce toplu UPDATE'ler koşup sonra `anonymize()` çağrılıyordu ve
           **hiçbir şey kaydedilmiyordu**: uç 204 dönüyor ama `users` satırı
           olduğu gibi kalıyordu. Sebep, toplu sorguların
           `@Modifying(clearAutomatically = true)` taşıması — çağrıldıklarında
           persistence context TEMİZLENİYOR ve elimizdeki `target` DETACHED
           hâle geliyor; sonraki alan değişiklikleri artık hiçbir yere
           yazılmıyor.
           Çözüm: kimlik temizliği ÖNCE yapılır ve `saveAndFlush` ile hemen
           veritabanına iner; toplu sorgular ondan sonra koşar. */
        target.anonymize();
        userRepository.saveAndFlush(target);

        // Oturumlar kapanir - silinen hesap bir daha istek atamamali.
        refreshTokenRepository.revokeAllForUser(target.getId(), Instant.now());

        // Icerik: gonderiler yumusak silinir, bolgeler geri alinir.
        postRepository.softDeleteByAuthor(target.getId(), Instant.now());
        territoryRepository.revokeAllByOwner(target.getId());

        auditRecorder.record(actor.id(), actor.username(), "USER_DELETED_BY_ADMIN", "USER",
                target.getId().toString(),
                Map.of("username", username, "email", email), null);
        log.info("Hesap silindi (anonimleştirildi): {} — yönetici {}", username, actor.username());
    }

    /**
     * <b>KÖKLÜ SİLME</b> — satır veritabanından gerçekten kaldırılır (17 Ağu 2026).
     *
     * <p>Kullanıcı isteği: <i>"köklü sil kısmı ekle, silince veritabanından
     * silinsin"</i>. {@link #deleteUser} anonimleştirmedir: satır kalır,
     * kişisel veri temizlenir. Bu metot satırı <b>siler</b>; bağlı kişisel
     * içerik V48'de tanımlanan {@code ON DELETE CASCADE} ile birlikte gider.
     *
     * <p>⚠️ Çocuk tablolar <b>elle sırayla silinmez</b>: bağımlılık grafiğini
     * veritabanı zaten biliyor. Elle liste tutmak, yeni bir tablo eklendiğinde
     * kimsenin listeyi güncellememesi ve silmenin bir gün ortada patlaması
     * demekti.
     *
     * <p>🔴 <b>Mali kaydı olan hesap köklü silinemez.</b> Fatura ve satın alma
     * kayıtları muhasebe belgesidir ve yasal saklama süresine tabidir; onları
     * bir hesap silmesiyle yok etmek doğru olmazdı. Böyle bir hesapta
     * anonimleştirme kullanılmalı — kişisel veri yine temizlenir, mali kayıt
     * kalır. Kapı hem burada hem veritabanında (RESTRICT) duruyor.
     *
     * <p>⚠️ Onay {@link #deleteUser} ile <b>aynı iki katlıdır</b> (kullanıcı adı
     * elle yazılır + yöneticinin kendi şifresi) — geri dönüşü olmayan bir
     * işlemde daha azı düşünülemezdi.
     */
    @Transactional
    public void purgeUser(UUID userId, DeleteUserRequest request, AuthenticatedUser actor) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Kullanıcı bulunamadı"));

        if (target.getId().equals(actor.id())) {
            throw new ApiException(ErrorCode.CONFLICT, "Kendi hesabınızı silemezsiniz");
        }
        assertNotLastActiveAdmin(target);
        if (!target.getUsername().equalsIgnoreCase(request.confirmUsername().trim())) {
            throw ApiException.badRequest("Kullanıcı adı eşleşmiyor — silme iptal edildi");
        }
        User admin = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("Yönetici bulunamadı"));
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Şifreniz hatalı");
        }

        long invoices = countFinancialRows("invoices", "user_id", userId);
        long purchases = countFinancialRows("purchases", "buyer_id", userId);
        if (invoices + purchases > 0) {
            throw new ApiException(ErrorCode.CONFLICT,
                    ("Bu hesabın mali kaydı var (%d fatura, %d satın alma) ve köklü silinemez. "
                            + "Kişisel veriyi temizlemek için \"Anonimleştir\" kullanın.")
                            .formatted(invoices, purchases));
        }

        String username = target.getUsername();
        String email = target.getEmail();

        /* 🔴 Denetim kaydı SİLMEDEN ÖNCE yazılır. Sonra yazılsaydı, silme
           işlemi başarısız olduğunda "silindi" diyen bir kayıt kalırdı; ayrıca
           `audit_logs.actor_id` de CASCADE kapsamında olabilir ve satırı
           silinen kullanıcıya bağlı bir kayıt kendisiyle birlikte giderdi. */
        auditRecorder.record(actor.id(), actor.username(), "USER_PURGED_BY_ADMIN", "USER",
                target.getId().toString(),
                Map.of("username", username, "email", email), null);

        refreshTokenRepository.revokeAllForUser(target.getId(), Instant.now());
        userRepository.delete(target);
        userRepository.flush();

        log.warn("🔴 Hesap KÖKLÜ SİLİNDİ: {} ({}) — yönetici {}", username, email, actor.username());
    }

    /**
     * Mali satır sayısı.
     * <p>⚠️ Native sorgu: {@code admin} modülü {@code billing}/{@code payment}
     * repository'lerine bağımlı olmamalı (modüler monolith kuralı).
     */
    private long countFinancialRows(String table, String column, UUID userId) {
        Object value = entityManager
                .createNativeQuery("SELECT count(*) FROM " + table + " WHERE " + column + " = :uid")
                .setParameter("uid", userId)
                .getSingleResult();
        return ((Number) value).longValue();
    }


    /**
     * <b>Son AKTİF yöneticiyi koru</b> — paneli erişilemez bırakmasın.
     *
     * <p>🔴 17 Ağu 2026 — ÖLÇÜLEREK YAKALANDI (yeni 4xx logu ilk gün buldu):
     * kontrol yalnız <i>"hedef ADMIN mi ve aktif admin sayısı ≤ 1 mi"</i> diye
     * soruyordu. Hedefin <b>kendisinin</b> aktif olup olmadığına bakmıyordu.
     *
     * <p>Sonuç: <b>zaten silinmiş (DELETED) bir yönetici hesabı</b> köklü
     * silinemiyordu — "Son yönetici hesabı silinemez" deniyordu, oysa o hesap
     * zaten yönetici olarak çalışmıyordu ve silinmesi aktif yönetici sayısını
     * <b>hiç değiştirmiyordu</b>. Yani koruma, korumadığı bir şey için
     * temizliği engelliyordu.
     *
     * <p>⚠️ Doğrusu: engel yalnız hedef <b>AKTİF bir yönetici</b> ve
     * <b>sonuncusu</b> ise geçerlidir.
     */
    private void assertNotLastActiveAdmin(User target) {
        boolean targetIsActiveAdmin =
                target.getRole() == Role.ADMIN && target.getStatus() == UserStatus.ACTIVE;
        if (targetIsActiveAdmin
                && userRepository.countByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new ApiException(ErrorCode.CONFLICT, "Son yönetici hesabı silinemez");
        }
    }

    // ------------------------------------------------------------ audit logs

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listAuditLogs(String action, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = action != null && !action.isBlank()
                ? auditLogRepository.findByAction(action.trim(), pageable)
                : auditLogRepository.findAll(pageable);
        return PageResponse.from(result, AuditLogResponse::from);
    }

    // ------------------------------------------------------------ dashboard

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

        Map<String, BigDecimal> revenueByCurrency = new LinkedHashMap<>();
        for (Object[] row : purchaseRepository.sumRevenueByCurrency()) {
            revenueByCurrency.put((String) row[0], (BigDecimal) row[1]);
        }

        List<RecentPurchase> recent = purchaseRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10)).stream()
                .map(p -> new RecentPurchase(p.getTerritoryId(),
                        userRepository.findById(p.getBuyerId()).map(User::getUsername).orElse("?"),
                        p.getAmount(), p.getCurrency(), p.getCreatedAt()))
                .toList();

        return new DashboardResponse(
                userRepository.count(),
                territoryRepository.countByStatus(TerritoryStatus.ACTIVE),
                postRepository.countByDeletedAtIsNull(),
                purchaseRepository.countByCreatedAtAfter(startOfToday),
                purchaseRepository.sumRevenueAfter(startOfToday),
                revenueByCurrency,
                recent);
    }
}
