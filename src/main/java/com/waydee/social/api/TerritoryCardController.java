package com.waydee.social.api;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.social.api.dto.TerritoryCardDtos.TerritoryCardResponse;
import com.waydee.social.application.TerritoryCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Harita bölge kartının tek ucu.
 *
 * Ayrı bir controller: kart yükü profil yükünden farklıdır (sayaçlar, çipler,
 * kiralama) ve kart açılışı görüntülenme SAYMAZ — sadece profile girmek sayar.
 * Aksi halde haritada gezinirken her daire bir görüntülenme üretirdi.
 */
@Tag(name = "Territory Card", description = "Harita bölge kartı")
@RestController
@RequestMapping("/api/v1/territories/{territoryId}/card")
@RequiredArgsConstructor
public class TerritoryCardController {

    private final TerritoryCardService cardService;

    @Operation(summary = "Harita kartı için zengin bölge özeti")
    @GetMapping
    public TerritoryCardResponse card(@PathVariable UUID territoryId,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return cardService.card(territoryId, principal != null ? principal.id() : null);
    }
}
