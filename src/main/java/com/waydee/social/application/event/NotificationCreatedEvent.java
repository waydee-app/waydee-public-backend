package com.waydee.social.application.event;

import java.util.UUID;

/**
 * <b>Bir bildirim yazıldı</b> (18 Ağu 2026).
 *
 * <p>🔴 <b>Neden olay, neden doğrudan WebSocket çağrısı değil:</b> bildirim
 * üreten yerler {@code social} modülünün içindedir ve gerçek zamanlı taşıma
 * {@code realtime} modülündedir. {@code NotificationService}'in
 * {@code SimpMessagingTemplate}'i doğrudan çağırması, iş kuralı katmanını
 * taşıma katmanına bağlardı — projedeki bölge/mesaj yayınlarının hepsi bu
 * yüzden olay üzerinden gidiyor.
 *
 * <p>⚠️ Yük <b>bilinçli olarak zayıf</b>: yalnız "sana yeni bir bildirim
 * geldi" der, bildirimin kendisini taşımaz. İstemci sayacı ve listeyi kendi
 * uçlarından tazeler. Bildirim gövdesini sokete koymak, aktör adı/avatarı gibi
 * alanları <b>iki ayrı yerde</b> üretmek (WS yükü + REST yanıtı) ve ikisinin
 * sessizce ayrışması demekti.
 *
 * @param recipientId bildirimi <b>alan</b> kişi — soket kuyruğu buna göre seçilir
 * @param type        {@code FOLLOW | PROFILE_VIEW | POST_LIKE …} — istemci
 *                    isterse türe göre farklı davranabilsin diye taşınır
 */
public record NotificationCreatedEvent(UUID recipientId, String type) {
}
