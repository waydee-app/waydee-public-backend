package com.waydee.social.application;

import com.waydee.identity.api.dto.FollowDtos.UserSummary;
import com.waydee.identity.domain.User;
import com.waydee.identity.infrastructure.UserRepository;
import com.waydee.social.api.dto.NotificationDtos.NotificationResponse;
import com.waydee.social.domain.Notification;
import com.waydee.social.domain.NotificationType;
import com.waydee.common.events.DomainEventPublisher;
import com.waydee.social.application.event.NotificationCreatedEvent;
import com.waydee.social.infrastructure.NotificationRepository;
import com.waydee.territory.domain.Territory;
import com.waydee.territory.infrastructure.TerritoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bildirim uretimi ve okuma. Takip/istek/kabul, profil goruntuleme ve
 * gonderi olaylari (begeni/kaydetme, V39) burada toplanir.
 */
@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final TerritoryRepository territoryRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * <b>Tek bildirimi siler</b> (21 Agu 2026, kullanici: *"bildirimlerin
     * sagina X isareti koy silebilesin"*).
     *
     * <p>⚠️ Sahiplik sorgunun icinde ({@code deleteOwned}); baskasinin
     * bildirimini silmek mumkun degil. Bulunamayan bildirim <b>hata degildir</b>:
     * kullanici ayni X'e iki kez basmis ya da baska bir sekmede silmis olabilir
     * ve bu bir arıza degil, ayni sonucun tekrariidir.
     */
    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        notificationRepository.deleteOwned(notificationId, userId);
    }

    /**
     * <b>Tumunu siler</b> (kullanici: *"veya tamamen hepsini silsin"*).
     *
     * <p>⚠️ Geri alinamaz ve bilincli olarak oyle: bildirim bir <b>gecmis
     * kaydi degil</b>, bir dikkat cagrisidir. "Cop kutusu" tutmak, temizlemek
     * isteyen kullaniciya ikinci bir temizlik isi verirdi.
     */
    @Transactional
    public void deleteAll(UUID userId) {
        notificationRepository.deleteAllForUser(userId);
    }

    /** Bir bildirim olusturur. Kendi eylemin sana bildirim uretmez. */
    @Transactional
    public void notify(UUID recipientId, NotificationType type, UUID actorId, UUID territoryId) {
        if (actorId != null && actorId.equals(recipientId)) {
            return;
        }
        notificationRepository.save(new Notification(recipientId, type, actorId, territoryId));
        publish(recipientId, type);
    }

    /**
     * <b>"Sana bildirim geldi" olayini yayinlar</b> (18 Agu 2026).
     *
     * <p>🔴 Bildirimler o gune kadar YALNIZ YOKLAMAYLA geliyordu: zil 30
     * saniyede bir sayaci soruyor, liste ise ancak sayfa yenilenince
     * tazeleniyordu. Kullanici "bildirimler aninda dusmuyor" dedi ve haklidi —
     * canli tasima projede ZATEN vardi (mesajlar, bolge, aktivite), yalniz
     * bildirimler ona baglanmamisti.
     *
     * <p>⚠️ Olay COMMIT SONRASI teslim edilir (dinleyici tarafinda
     * {@code AFTER_COMMIT}): istemci, veritabaninda henuz gorunmeyen bir
     * bildirim icin listeyi tazelerse ESKI listeyi alir ve rozet yalan soyler.
     */
    private void publish(UUID recipientId, NotificationType type) {
        eventPublisher.publish(new NotificationCreatedEvent(recipientId, type.name()));
    }

    /*
     * 🔴 `notifyProfileView` ve `PROFILE_VIEW_THROTTLE` KALDIRILDI (24 Agu 2026).
     *
     * Kullanici: *"bir hesaba bakma bildirim ozelligi kaldirilsin - bildirim
     * gelmesin, sadece istatistiklerde gorunsun"*.
     *
     * ⚠️ OLCUM DURUYOR (`AnalyticsService.recordView`); kaldirilan yalniz
     * duyurudur. Ikisi ayri kararlardir: 21 Agu'da tam tersi bir hata
     * yasanmisti - duyuruluyor ama tutulmuyordu.
     *
     * ⚠️ `NotificationType.PROFILE_VIEW` enum degeri SILINMEDI: kolon VARCHAR
     * ve gecmis satirlar V54 ile temizlendi. Turu kaldirmak, ileride geri
     * gelmesi halinde sozlesmeyi yeniden acmak demekti - silinen sey veri,
     * sozlesme degil.
     */

    /**
     * <b>Gonderi bildirimi</b> (V39): begeni / kaydetme.
     *
     * <p>UYARI: ayni kisi ayni gonderiyi begenip geri alip tekrar begenirse
     * bildirim YIGILMAZ - veritabaninda (alici, tur, aktor, gonderi) tekildir
     * ve ikinci kayit sessizce yutulur. Kontrolu yalniz uygulamada yapmak,
     * iki es zamanli istekte iki satir uretirdi.
     *
     * <p>UYARI: bildirim uretimi ANA ISLEMI DUSURMEZ. Begeni kaydedildikten
     * sonra bildirim satiri catlarsa (tekillik ihlali gibi) kullanicinin
     * begenisi geri alinmamali.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void notifyPost(UUID recipientId, NotificationType type, UUID actorId, UUID postId) {
        if (actorId != null && actorId.equals(recipientId)) {
            return;
        }
        try {
            notificationRepository.save(new Notification(recipientId, type, actorId, null, postId));
            publish(recipientId, type);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Ayni bildirim zaten var - beklenen durum, sessizce gecilir.
            log.debug("Yinelenen gonderi bildirimi yok sayildi: {} {} {}", type, actorId, postId);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId) {
        List<Notification> items = notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
        if (items.isEmpty()) {
            return List.of();
        }
        Map<UUID, User> actors = userRepository.findAllById(
                        items.stream().map(Notification::getActorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Territory> territories = territoryRepository.findAllById(
                        items.stream().map(Notification::getTerritoryId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Territory::getId, Function.identity()));

        return items.stream().map(n -> new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getActorId() != null && actors.containsKey(n.getActorId()) ? UserSummary.from(actors.get(n.getActorId())) : null,
                n.getTerritoryId(),
                n.getTerritoryId() != null && territories.containsKey(n.getTerritoryId()) ? territories.get(n.getTerritoryId()).getName() : null,
                n.getPostId(),
                n.isRead(),
                n.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId);
    }
}
