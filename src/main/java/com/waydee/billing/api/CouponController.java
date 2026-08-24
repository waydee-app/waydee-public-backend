package com.waydee.billing.api;

import com.waydee.billing.api.dto.CouponDtos.CouponReport;
import com.waydee.billing.api.dto.CouponDtos.CouponRequest;
import com.waydee.billing.api.dto.CouponDtos.CouponResponse;
import com.waydee.billing.application.CouponAdminService;
import com.waydee.billing.application.CouponService;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Coupons", description = "İndirim kuponları")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final CouponAdminService adminService;
    private final com.waydee.billing.application.PlanCouponService planCouponService;
    /** 🔒 17 Ağu 2026 — denetim kaydına yazılan IP artık sahte X-Forwarded-For ile ezilemez. */
    private final com.waydee.common.net.ClientIpResolver clientIpResolver;

    /**
     * <b>Paket kodunu kullan</b> (V40).
     *
     * <p>🔒 Kimlik <b>oturumdan</b> alınır; istekte kullanıcı kimliği kabul
     * edilmez — aksi halde bir kod başkasının hesabına yüklenebilirdi.
     *
     * <p>⚠️ Geçersiz/kullanılmış kodda <b>400</b> ve okunabilir bir gerekçe
     * döner; sessiz başarı YOKTUR (kullanıcı planının değiştiğini sanmamalı).
     */
    @Operation(summary = "Paket kodunu kullan — plan anında yükselir")
    @PostMapping("/coupons/redeem")
    public com.waydee.billing.application.PlanCouponService.RedeemResult redeem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RedeemRequest request,
            jakarta.servlet.http.HttpServletRequest http) {
        return planCouponService.redeem(request.code(), principal.id(), clientIpResolver.resolve(http));
    }

    /** İstemcinin gönderdiği tek alan: kod. */
    public record RedeemRequest(
            @jakarta.validation.constraints.NotBlank(message = "Kod zorunludur")
            @jakarta.validation.constraints.Size(max = 40)
            String code) {
    }

    /* 🔴 `POST /coupons/preview` KALDIRILDI (V38).
       Kuponlar yalnız BÖLGE ödemelerine uygulanıyordu; daire artık ödemesiz bir
       üyelik hakkı olduğu için önizleyecek bir tutar kalmadı. Uç, fiyatı daire
       geometrisinden hesaplıyordu (`territoryService.quote`) — o yol da yok.
       ⚠️ Kupon YÖNETİMİ duruyor: geçmiş kullanımların raporu ve mevcut kayıtlar
       silinmemeli. Üyelik ödemelerine kupon istenirse `startPlanCheckout`
       üzerine ayrıca kurulmalı; eski geometri tabanlı yolu diriltmek değil. */

    // ------------------------------------------------------------ yönetim

    @Operation(summary = "ADMIN · kupon listesi")
    @GetMapping("/admin/coupons")
    public PageResponse<CouponResponse> list(@RequestParam(required = false) String query,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return adminService.list(query, page, size);
    }

    @Operation(summary = "ADMIN · kupon detayı")
    @GetMapping("/admin/coupons/{id}")
    public CouponResponse one(@PathVariable UUID id) {
        return adminService.one(id);
    }

    @Operation(summary = "ADMIN · kupon oluştur")
    @PostMapping("/admin/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CouponRequest request) {
        return adminService.create(request);
    }

    @Operation(summary = "ADMIN · kupon güncelle")
    @PutMapping("/admin/coupons/{id}")
    public CouponResponse update(@PathVariable UUID id, @Valid @RequestBody CouponRequest request) {
        return adminService.update(id, request);
    }

    /** Silmez, pasife alır — geçmiş kullanım kayıtları kupona bağlıdır. */
    @Operation(summary = "ADMIN · kuponu pasife al")
    @DeleteMapping("/admin/coupons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        adminService.deactivate(id);
    }

    @Operation(summary = "ADMIN · kupon kullanım analizi")
    @GetMapping("/admin/coupons/report")
    public CouponReport report(@RequestParam(defaultValue = "30") int days) {
        return adminService.report(days);
    }
}
