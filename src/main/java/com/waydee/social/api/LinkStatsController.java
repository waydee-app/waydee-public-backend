package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.LinkStatsDtos.LinkStatsResponse;
import com.waydee.social.application.LinkClickService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * <b>Bağlantı tıklama ölçümü ve raporu</b> (V46).
 *
 * <p>Ölçüm ucu <b>herkese açık</b> yüzeydedir ({@code /public/**}) çünkü
 * tıklayan çoğunlukla oturum açmamış bir ziyaretçidir. Rapor ucu ise oturum
 * ister ve <b>yalnız kendi</b> bağlantılarını döndürür.
 */
@Tag(name = "Link stats", description = "Profil bağlantısı tıklama ölçümü")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class LinkStatsController {

    private final LinkClickService linkClickService;

    /**
     * <b>Bağlantıya tıklandı.</b>
     *
     * <p>🔴 Herkese açık: vitrini gezen ziyaretçi oturum açmaz. Kimlik
     * <b>varsa</b> (JWT taşıyan istek) kaydedilir — kullanıcının istediği
     * "gerçek user bilgisi" budur; yoksa isimsiz ziyaretçi olarak sayılır.
     *
     * <p>⚠️ Yanıt <b>204</b> ve gövdesizdir: istemci bunu {@code keepalive}
     * ile gönderip hedefe <b>beklemeden</b> gidiyor. Ölçüm, ölçtüğü deneyimi
     * yavaşlatmamalı (vault, etiket ölçümündeki aynı ilke).
     *
     * <p>⚠️ Geçersiz/pasif bağlantıda da 204 döner — ziyaretçiye hata
     * göstermenin hiçbir faydası yok ve var olmayan bir kimliği "yok" diye
     * doğrulamak, bağlantı kimliklerini taramaya davet olurdu.
     */
    @Operation(summary = "Bağlantı tıklamasını kaydet (ziyaretçiye açık)")
    @PostMapping("/public/links/{id}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void click(@PathVariable UUID id,
                      @AuthenticationPrincipal AuthenticatedUser user,
                      HttpServletRequest request) {
        linkClickService.record(id, user == null ? null : user.id(), request);
    }

    /**
     * <b>Bağlantı raporum.</b>
     *
     * <p>🔒 Kimlik oturumdan alınır ve rapor <b>yalnız kendi</b> bağlantılarını
     * kapsar; istekte sahip kimliği kabul edilmez.
     */
    @Operation(summary = "Bağlantı tıklama raporum")
    @GetMapping("/profile-links/stats")
    public LinkStatsResponse stats(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam(defaultValue = "30") int days) {
        return linkClickService.report(user.id(), days);
    }
}
