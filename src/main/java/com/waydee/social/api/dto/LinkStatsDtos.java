package com.waydee.social.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bağlantı tıklama raporunun sözleşmeleri (V46).
 *
 * <p>🔴 <b>Hiçbir alanda ülke ADI yoktur</b>, yalnız <b>ISO kodu</b>. Adı
 * istemci {@code Intl.DisplayNames} ile kendi dilinde üretir — sunucudan ad
 * göndermek arayüzü tek dile mahkûm ederdi (vault, 83. tur) ve beş dilin
 * hepsinde ülke sözlüğü tutmayı gerektirirdi.
 */
public final class LinkStatsDtos {

    private LinkStatsDtos() {
    }

    /**
     * Tek bağlantının performansı.
     *
     * @param clicks   dönemdeki tıklama
     * @param visitors dönemdeki <b>tekil</b> ziyaretçi (aynı kişinin on tıklaması bir kişidir)
     * @param totalClicks ömür boyu toplam ({@code profile_links.click_count})
     * @param lastClickAt son tıklama; hiç yoksa {@code null}
     */
    public record LinkRow(UUID id, String title, String url, String iconUrl,
                          long clicks, long visitors, int totalClicks, Instant lastClickAt) {
    }

    /** @param code ISO 3166-1 alpha-2; bilinmiyorsa {@code null} */
    public record CountryRow(String code, long clicks) {
    }

    public record DayRow(LocalDate day, long clicks) {
    }

    /**
     * Son tıklayan.
     *
     * @param username oturum açmamış ziyaretçide {@code null} — arayüz bunu
     *                 "ziyaretçi" olarak yazar, boş satır olarak değil
     */
    public record VisitorRow(Instant at, String country, UUID userId, String username,
                             String displayName, String avatarUrl, String linkTitle) {
    }

    /**
     * Rapor.
     *
     * @param days      kaç günlük pencere
     * @param clicks    dönemdeki toplam tıklama
     * @param visitors  dönemdeki tekil ziyaretçi
     * @param identified tıklayanların kaçı <b>giriş yapmış</b> bir kullanıcıydı
     */
    public record LinkStatsResponse(int days, long clicks, long visitors, long identified,
                                    List<DayRow> daily, List<CountryRow> countries,
                                    List<LinkRow> links, List<VisitorRow> recent) {
    }
}
