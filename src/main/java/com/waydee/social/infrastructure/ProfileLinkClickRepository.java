package com.waydee.social.infrastructure;

import com.waydee.social.domain.ProfileLinkClick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bağlantı tıklama raporunun tüm sorguları (V46).
 *
 * <p>⚠️ Hepsi <b>tarih aralığıyla</b> sınırlıdır. Sınırsız bir "tüm zamanlar"
 * sorgusu, tablo büyüdükçe raporu yavaşlatır; toplam sayaç zaten
 * {@code profile_links.click_count} kolonunda duruyor.
 */
public interface ProfileLinkClickRepository extends JpaRepository<ProfileLinkClick, UUID> {

    /**
     * <b>Tekrar bastırma penceresi.</b> Aynı ziyaretçi aynı bağlantıya kısa
     * sürede birkaç kez tıklarsa (çift tıklama, geri gel-tekrar tıkla) tek
     * sayılır.
     *
     * <p>🔴 Bu olmadan sayaç <b>şişirilebilirdi</b>: sayfayı yenileyip tıklamayı
     * tekrarlayan biri kendi bağlantısının rakamını istediği yere çekerdi ve
     * rapor bir ölçüm olmaktan çıkardı.
     */
    boolean existsByLinkIdAndVisitorKeyAndCreatedAtAfter(UUID linkId, String visitorKey, Instant since);

    @Query("select count(c) from ProfileLinkClick c where c.ownerId = :owner and c.createdAt >= :since")
    long countInPeriod(@Param("owner") UUID owner, @Param("since") Instant since);

    /** Tekil ziyaretçi — aynı kişinin on tıklaması bir kişidir. */
    @Query("select count(distinct c.visitorKey) from ProfileLinkClick c "
            + "where c.ownerId = :owner and c.createdAt >= :since")
    long countVisitorsInPeriod(@Param("owner") UUID owner, @Param("since") Instant since);

    /**
     * Bağlantı bazında toplam.
     *
     * <p>⚠️ Yalnız <b>tıklaması olan</b> bağlantılar döner; sıfır tıklamalılar
     * burada yoktur ve rapor onları bağlantı listesinden tamamlar. Aksi halde
     * her rapor için tüm bağlantı tablosuyla dış birleştirme gerekirdi.
     */
    @Query("select c.linkId, count(c), count(distinct c.visitorKey), max(c.createdAt) "
            + "from ProfileLinkClick c where c.ownerId = :owner and c.createdAt >= :since "
            + "group by c.linkId")
    List<Object[]> totalsByLink(@Param("owner") UUID owner, @Param("since") Instant since);

    /**
     * Günlük seri.
     *
     * <p>⚠️ Gün <b>UTC</b> olarak kesilir; sunucu ve kullanıcı farklı saat
     * dilimlerindeyse gün sınırı kaymasın (V44'teki aynı tercih).
     */
    @Query(value = "select date_trunc('day', created_at at time zone 'UTC') as d, count(*) "
            + "from profile_link_clicks where owner_id = :owner and created_at >= :since "
            + "group by d order by d", nativeQuery = true)
    List<Object[]> dailyTotals(@Param("owner") UUID owner, @Param("since") Instant since);

    /**
     * Ülke kırılımı — <b>kod</b> bazında.
     *
     * <p>⚠️ Ülkesi bilinmeyen tıklamalar da sayılır ({@code country is null});
     * onları elemek toplamı bozar ve "rakamlar tutmuyor" şikâyeti doğurur.
     */
    @Query("select c.country, count(c) from ProfileLinkClick c "
            + "where c.ownerId = :owner and c.createdAt >= :since "
            + "group by c.country order by count(c) desc")
    List<Object[]> countryTotals(@Param("owner") UUID owner, @Param("since") Instant since);

    /**
     * Son tıklayanlar — <b>yalnız bağlantı sahibine</b> gösterilir.
     *
     * <p>⚠️ Kişi bilgisi ancak tıklayan <b>oturum açmışsa</b> vardır; geri
     * kalanı isimsiz ziyaretçidir ve rapor bunu açıkça öyle yazar. Boş bir
     * satır yerine "ziyaretçi" demek, veriyi olduğundan zengin göstermemenin
     * tek dürüst yolu.
     */
    @Query("select c.createdAt, c.country, u.id, u.username, u.displayName, u.avatarMediaId, l.title "
            + "from ProfileLinkClick c "
            + "join ProfileLink l on l.id = c.linkId "
            + "left join User u on u.id = c.userId "
            + "where c.ownerId = :owner and c.createdAt >= :since "
            + "order by c.createdAt desc")
    List<Object[]> recentClicks(@Param("owner") UUID owner, @Param("since") Instant since, Pageable pageable);
}
