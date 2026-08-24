package com.waydee.traffic.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.web.PageResponse;
import com.waydee.traffic.api.dto.TrafficDtos.MyLoginRow;
import com.waydee.traffic.application.TrafficService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kullanıcının kendi giriş geçmişi — "Ayarlar → Güvenlik" ekranını besler.
 *
 * <p>⚠️ Kimlik <b>oturumdan</b> alınır; istekte kullanıcı kimliği kabul
 * edilmez. Aksi halde herkes başkasının IP/cihaz geçmişini okuyabilirdi.
 */
@Tag(name = "Security", description = "Hesap güvenliği: giriş yapılan cihazlar")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class MyLoginController {

    private final TrafficService trafficService;

    @Operation(summary = "Hesabıma giriş yapılan cihazlar (başarısız denemeler dahil)")
    @GetMapping("/logins")
    public PageResponse<MyLoginRow> myLogins(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return trafficService.myLogins(principal.id(), page, size);
    }
}
