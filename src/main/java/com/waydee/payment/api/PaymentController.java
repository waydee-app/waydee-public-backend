package com.waydee.payment.api;

import com.waydee.common.error.ApiException;
import com.waydee.common.security.AuthenticatedUser;
import com.waydee.payment.application.CheckoutService;
import com.waydee.payment.application.CheckoutService.CheckoutView;
import com.waydee.payment.application.PaymentProviderResolver;
import com.waydee.territory.api.dto.TerritoryDtos.PurchaseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ödeme oturumu uçları.
 *
 * <p>Satın alma artık tek istekte bitmez: burada bir <b>ödeme sayfası</b> açılır,
 * kullanıcı oraya yönlendirilir ve bölge ancak sağlayıcının bildirimi gelince
 * oluşur (bkz. {@link PaymentWebhookController}).
 */
@Tag(name = "Payments", description = "Ödeme oturumu ve durum sorgulama")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final CheckoutService checkoutService;
    private final PaymentProviderResolver providers;
    /** 🔒 17 Ağu 2026 — denetim kaydına yazılan IP artık sahte X-Forwarded-For ile ezilemez. */
    private final com.waydee.common.net.ClientIpResolver clientIpResolver;

    /**
     * <b>PRO'ya yükselt</b> — ödeme oturumu açar.
     *
     * <p>⚠️ Plan yalnız <b>imzalı webhook</b> ile yükselir; bu uç sadece
     * sağlayıcının ödeme sayfasını döner. Burada yükseltmek, ödemeden vazgeçen
     * kullanıcıyı bedava Pro yapardı.
     */
    @Operation(summary = "Üyelik yükseltme oturumu (PRO | PREMIUM · MONTHLY | YEARLY)")
    @PostMapping("/plan-checkout")
    public ResponseEntity<CheckoutView> planCheckout(
            @AuthenticationPrincipal AuthenticatedUser principal,
            /* ⚠️ Varsayılanlar V37 ÖNCESİ istemcileri kırmasın diye duruyor:
               parametresiz çağrı eskisi gibi "PRO · aylık" oturumu açar. */
            @RequestParam(defaultValue = "PRO") com.waydee.identity.domain.UserPlan plan,
            @RequestParam(defaultValue = "MONTHLY") com.waydee.identity.domain.BillingPeriod period,
            HttpServletRequest http) {
        CheckoutView view = checkoutService.startPlanCheckout(
                principal.id(), plan, period, principal.username(), clientIpResolver.resolve(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    /* 🔴 BÖLGE ÖDEME UÇLARI KALDIRILDI (V38).
       `POST /territory-checkout` ve `POST /territory-renewal-checkout/{id}`
       daireyi km² fiyatıyla satıyordu. Daire artık PREMIUM üyeliğin hakkı:
       kurulumu `POST /api/v1/territories/store` yapar, ücreti yoktur, süresi
       üyeliğe bağlıdır. İki yol birden bırakmak, aynı hakkı iki kez satan bir
       kapı açık bırakmak olurdu.
       ⚠️ Tamamlama tarafı (`completePaid`) DURUYOR: ödemesi çoktan alınmış eski
       rezervasyonların webhook'u hâlâ gelebilir ve para alınıp bölge
       verilmemesi kabul edilemez. */

    @Operation(summary = "Ödeme durumu (dönüş ekranı bunu yoklar)")
    @GetMapping("/checkouts/{id}")
    public CheckoutView status(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return checkoutService.status(id, principal.id());
    }

    @Operation(summary = "Ödemelerim")
    @GetMapping("/checkouts")
    public List<CheckoutView> myCheckouts(@AuthenticationPrincipal AuthenticatedUser principal) {
        return checkoutService.myCheckouts(principal.id());
    }

    @Operation(summary = "Ödemeden vazgeç — rezerve alan hemen serbest kalır")
    @DeleteMapping("/checkouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
        checkoutService.cancel(id, principal.id());
    }

    /**
     * Sahte sağlayıcının "ödedim" düğmesi.
     *
     * <p>⚠️ Yalnız {@code waydee.payment.provider=mock} iken çalışır; gerçek
     * sağlayıcı seçiliyken <b>404</b> döner. Aksi halde bu uç, üretimde
     * ödeme yapmadan bölge almanın yolu olurdu.
     */
    @Operation(summary = "SAHTE ödemeyi onayla (yalnız mock sağlayıcıda)")
    @PostMapping("/checkouts/{id}/mock-confirm")
    public CheckoutView mockConfirm(@PathVariable UUID id,
                                    @AuthenticationPrincipal AuthenticatedUser principal,
                                    HttpServletRequest http) {
        if (!providers.isMock()) {
            throw ApiException.notFound("Bulunamadı");
        }
        // Sahiplik kontrolü: başkasının ödemesini onaylayamazsın.
        checkoutService.status(id, principal.id());
        checkoutService.completePaid(id, "mock-order-" + id, clientIpResolver.resolve(http));
        return checkoutService.status(id, principal.id());
    }
}
