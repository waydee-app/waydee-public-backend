package com.waydee.social.api.dto;

import java.util.List;

public final class SearchDtos {

    private SearchDtos() {
    }

    /**
     * Tek arama sonucu.
     *
     * @param type USER | TERRITORY
     * @param note kısa bağlam ("Gizli hesap", "12 beğeni") — yoksa null
     */
    /**
     * @param username 🔴 17 Ağu 2026'da eklendi. Arama sonucu tıklanınca
     *                 istemci <b>doğrudan</b> {@code /{username}} adresine
     *                 gitsin diye. Önceden yalnız {@code id} vardı ve istemci
     *                 {@code /u/{id}} köprüsüne gidiyordu: adres çubuğunda
     *                 çirkin bir UUID beliriyor, sonra ikinci bir istekle
     *                 gerçek profile atlıyordu. Ad zaten burada — köprüye
     *                 hiç gerek yoktu.
     *                 ⚠️ Yalnız USER için dolu; TERRITORY için {@code null}.
     */
    public record SearchHit(
            String type,
            String id,
            String username,
            String title,
            String subtitle,
            String avatarUrl,
            String note,
            /** Yalnız TERRITORY için dolu — haritada oraya uçmak için. */
            Double lng,
            Double lat
    ) {
    }

    public record SearchResponse(String query, List<SearchHit> users, List<SearchHit> territories) {
    }
}
