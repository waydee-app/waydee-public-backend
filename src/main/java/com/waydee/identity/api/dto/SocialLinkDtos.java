package com.waydee.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SocialLinkDtos {

    private SocialLinkDtos() {
    }

    /**
     * Dışarı verilen bağlantı.
     *
     * @param platform WEBSITE | INSTAGRAM | X | FACEBOOK | YOUTUBE | TIKTOK | SNAPCHAT | LINKEDIN | TELEGRAM | GITHUB
     * @param value    kullanıcının yazdığı ham değer (düzenleme ekranı bunu gösterir)
     * @param url      tıklanabilir tam adres (yalnız http/https)
     */
    public record SocialLinkView(String platform, String value, String url) {
    }

    public record SocialLinkInput(
            @NotBlank(message = "Platform zorunludur") String platform,
            @Size(max = 200, message = "Bağlantı en fazla 200 karakter olabilir") String value
    ) {
    }

    /** Tam değiştirme: gönderilmeyen platformlar silinir, sıra listenin sırasıdır. */
    public record UpdateSocialLinksRequest(
            @Size(max = 10, message = "En fazla 10 bağlantı eklenebilir") List<SocialLinkInput> links
    ) {
    }
}
