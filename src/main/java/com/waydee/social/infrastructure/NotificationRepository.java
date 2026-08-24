package com.waydee.social.infrastructure;

import com.waydee.social.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * <b>Bu kisi son X sure icinde zaten bildirildi mi?</b> (18 Agu 2026)
     *
     * <p>🔴 Profil goruntuleme bildirimi <b>kisilmak zorundadir</b>: ziyaretci
     * sekmeyi her yenilediginde ya da profil ile gonderi arasinda gidip
     * geldiginde yeni bir bildirim uretilseydi, sahibinin listesi tek bir
     * ziyaretcinin ayni satirlariyla dolardi.
     *
     * <p>⚠️ Kisma icin AYRI BIR TABLO acilmadi: bildirimin kendisi zaten
     * "en son ne zaman haber verdik" sorusunun cevabidir. Ikinci bir sayac,
     * defterle sapabilecek ikinci bir gercek olurdu.
     */
    boolean existsByUserIdAndTypeAndActorIdAndCreatedAtAfter(
            UUID userId,
            com.waydee.social.domain.NotificationType type,
            UUID actorId,
            java.time.Instant after);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    /**
     * <b>Tek bildirimi siler</b> — yalnız sahibininkini (21 Ağu 2026).
     *
     * <p>🔴 {@code deleteById} KULLANILMIYOR: o, kimliği bilen herkesin
     * başkasının bildirimini silmesine izin verirdi. Sahiplik <b>sorgunun
     * kendisinde</b>; ayrı bir "önce oku, sahibi mi diye bak, sonra sil"
     * adımı hem fazladan gidiş dönüş hem de atlanabilecek bir kontroldür.
     *
     * @return silinen satır sayısı — 0 ise bildirim yok ya da başkasınındır.
     */
    @Modifying
    @Query("delete from Notification n where n.id = :id and n.userId = :userId")
    int deleteOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Kullanıcının TÜM bildirimlerini siler. */
    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
