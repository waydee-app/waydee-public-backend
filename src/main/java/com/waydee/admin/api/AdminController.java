package com.waydee.admin.api;

import com.waydee.admin.api.dto.AdminDtos.AdminUserResponse;
import com.waydee.admin.api.dto.AdminDtos.AuditLogResponse;
import com.waydee.admin.api.dto.AdminDtos.DashboardResponse;
import com.waydee.admin.api.dto.AdminDtos.DeleteUserRequest;
import com.waydee.admin.api.dto.AdminDtos.UpdateUserRequest;
import com.waydee.admin.application.AdminService;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.territory.application.TerritoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin", description = "Dashboard, kullanıcı yönetimi ve sistem logları")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final TerritoryService territoryService;

    @Operation(summary = "Dashboard istatistikleri")
    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return adminService.dashboard();
    }

    @Operation(summary = "Kullanıcıları listele / ara")
    @GetMapping("/users")
    public PageResponse<AdminUserResponse> listUsers(@RequestParam(required = false) String query,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return adminService.listUsers(query, page, size);
    }

    @Operation(summary = "Kullanıcı rol/durum güncelle")
    @PatchMapping("/users/{id}")
    public AdminUserResponse updateUser(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateUserRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser actor) {
        return adminService.updateUser(id, request, actor);
    }

    /**
     * <b>Hesabi sil</b> - iki katli dogrulama ister (kullanici adi + yonetici
     * sifresi) ve geri donusu yoktur.
     *
     * <p>UYARI: govde ZORUNLU oldugu icin `DELETE` yerine `POST` kullanildi.
     * DELETE govdesi bazi vekil sunucular tarafindan atilir; onay bilgisini
     * sorgu dizesine koymak ise sifreyi URL'e ve erisim loglarina yazardi.
     */
    @Operation(summary = "Kullanıcı hesabını sil (kullanıcı adı + şifre onayı ister)")
    @PostMapping("/users/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID id,
                           @Valid @RequestBody DeleteUserRequest request,
                           @AuthenticationPrincipal AuthenticatedUser actor) {
        adminService.deleteUser(id, request, actor);
    }

    /**
     * <b>KÖKLÜ SİLME</b> — satırı veritabanından gerçekten kaldırır (17 Ağu 2026).
     *
     * <p>{@code /delete} anonimleştirmedir (satır kalır, kişisel veri gider);
     * bu uç satırı <b>siler</b>. Onay aynı iki katlıdır: kullanıcı adı elle
     * yazılır + yöneticinin kendi şifresi.
     *
     * <p>⚠️ Ayrı uç olması bilinçli: aynı uca bir {@code hard=true} bayrağı
     * eklemek, geri dönüşü olmayan bir işlemi tek karakterlik bir fark hâline
     * getirirdi.
     */
    @Operation(summary = "Kullanıcıyı KÖKLÜ sil (veritabanından kaldırır)")
    @PostMapping("/users/{id}/purge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgeUser(@PathVariable UUID id,
                          @Valid @RequestBody DeleteUserRequest request,
                          @AuthenticationPrincipal AuthenticatedUser actor) {
        adminService.purgeUser(id, request, actor);
    }

    @Operation(summary = "Sistem / denetim logları")
    @GetMapping("/audit-logs")
    public PageResponse<AuditLogResponse> auditLogs(@RequestParam(required = false) String action,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size) {
        return adminService.listAuditLogs(action, page, size);
    }

    @Operation(summary = "Alanı pasife al (satın alma iptali)")
    @DeleteMapping("/territories/{id}")
    public ResponseEntity<Void> revokeTerritory(@PathVariable UUID id,
                                                @AuthenticationPrincipal AuthenticatedUser actor) {
        territoryService.revoke(id, actor);
        return ResponseEntity.noContent().build();
    }
}
