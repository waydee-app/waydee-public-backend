package com.waydee.admin.api.dto;

import com.waydee.common.audit.AuditLog;
import com.waydee.identity.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            String role,
            String status,
            /** Destek ekranında "kullanıcı neden satın alamıyor" sorusunun cevabı. */
            boolean emailVerified,
            /** FREE | PRO — destek ekranında "neden limite takılıyor"un cevabı. */
            String plan,
            Instant planSince,
            /** PRO üyeliğin bitişi (aylık, V35) — "ne zaman düşecek" sorusu. */
            Instant planExpiresAt,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static AdminUserResponse from(User user) {
            return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                    user.getDisplayName(), user.getRole().name(), user.getStatus().name(),
                    // Yürürlükteki plan: süresi dolmuş PRO satırı süpürülene
                    // kadar PRO yazar, destek ekranı gerçeği görmeli.
                    user.isEmailVerified(), user.effectivePlan().name(), user.getPlanSince(),
                    user.getPlanExpiresAt(),
                    user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record UpdateUserRequest(
            @Pattern(regexp = "^(USER|ADMIN)$", message = "Rol USER ya da ADMIN olmalı") String role,
            @Pattern(regexp = "^(ACTIVE|SUSPENDED)$", message = "Durum ACTIVE ya da SUSPENDED olmalı") String status,
            /**
             * 🔴 Elle plan verme/geri alma — destek, deneme ve iade için.
             *
             * <p>Bu yol OLMADAN Pro'ya geçmenin tek yolu ödemeydi: iade edilen bir
             * üyeliği geri almak ya da bir kullanıcıya deneme açmak imkânsızdı
             * ({@code PlanUpgradeService.change} yazılmış ama hiçbir uca
             * bağlanmamıştı). Değişiklik denetim kaydına düşer.
             */
            @Pattern(regexp = "^(FREE|PRO|PREMIUM)$", message = "Plan FREE, PRO ya da PREMIUM olmalı") String plan,
            /**
             * Elle verilen üyeliğin dönemi (V37). Boşsa <b>aylık</b>.
             *
             * <p>⚠️ Yıllık deneme vermek destek için gerçek bir ihtiyaç: 12 kez
             * "aylık ver" tıklamak yerine tek işlemde 365 gün verilir.
             */
            @Pattern(regexp = "^(MONTHLY|YEARLY)$", message = "Dönem MONTHLY ya da YEARLY olmalı") String planPeriod,
            /**
             * Yoneticinin KENDI sifresi — yalniz <b>ADMIN'e yukseltme</b>de
             * zorunlu. Diger alanlar icin gonderilmez.
             */
            String password
    ) {
    }

    /**
     * <b>Hesap silme isteği</b> — iki katlı doğrulama.
     *
     * <p>UYARI: tek tiklamayla silme YOK. Yonetici hem hedefin kullanici adini
     * ELLE yazar (yanlis satira tiklamayi yakalar) hem de KENDI SIFRESINI
     * girer (acik kalmis bir yonetim oturumunu ele geciren birinin hesaplari
     * silmesini engeller). Ikisi ayri seyi korur, biri digerinin yerini tutmaz.
     */
    public record DeleteUserRequest(
            @NotBlank(message = "Kullanıcı adını yazarak onaylayın")
            String confirmUsername,
            @NotBlank(message = "Şifreniz zorunludur")
            String password
    ) {
    }

    public record AuditLogResponse(
            Long id,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> detail,
            String ip,
            Instant createdAt
    ) {
        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(log.getId(), log.getActorUsername(), log.getAction(),
                    log.getEntityType(), log.getEntityId(), log.getDetail(), log.getIp(), log.getCreatedAt());
        }
    }

    public record RecentPurchase(
            UUID territoryId,
            String buyerUsername,
            BigDecimal amount,
            String currency,
            Instant createdAt
    ) {
    }

    public record DashboardResponse(
            long totalUsers,
            long totalTerritories,
            long totalPosts,
            long purchasesToday,
            BigDecimal revenueToday,
            Map<String, BigDecimal> revenueByCurrency,
            List<RecentPurchase> recentPurchases
    ) {
    }
}
