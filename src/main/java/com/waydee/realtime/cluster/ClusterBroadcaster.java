package com.waydee.realtime.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 🔴 <b>Yatay ölçeklemenin kilidini açan sınıf</b> (ölçek analizi bulgusu K3).
 *
 * <p><b>Sorun neydi:</b> {@code WebSocketConfig} STOMP broker'ı
 * {@code enableSimpleBroker} ile kuruyor; bu broker <b>süreç içidir</b>. İki
 * task çalışırken A task'ında gerçekleşen bir satın alma, B task'ına bağlı
 * kullanıcıya <b>hiç ulaşmaz</b> — kullanıcı sayfayı yenileyene kadar haritada
 * eski dünyayı görür. Bu yüzden {@code desiredCount} bugüne kadar <b>1</b>'de
 * kilitliydi ve yatay ölçekleme imkânsızdı.
 *
 * <p><b>Çözüm:</b> yayın önce <b>Redis Pub/Sub</b>'a gider; her task kendi
 * dinleyicisiyle aynı mesajı alır ve <b>kendi</b> bağlı oturumlarına teslim
 * eder. Mimari bunu zaten öngörmüştü ({@code DomainEventPublisher} soyutlaması),
 * ElastiCache de kurulu — <b>ek maliyet sıfır</b>.
 *
 * <p>🔴 <b>Teslim SADECE dinleyici üzerinden yapılır</b>, yayınlayan task dahil.
 * Hem yayınlayıp hem yerelde teslim etseydik, yayınlayan task'a bağlı kullanıcı
 * mesajı <b>iki kez</b> alırdı.
 *
 * <p>🔴 <b>Fail-open:</b> Redis'e yayın başarısız olursa mesaj <b>yerelde</b>
 * teslim edilir. Böylece Redis'siz bir ortam (ya da kesinti) gerçek zamanlı
 * özellikleri tamamen öldürmez; yalnız çok-task fan-out'u durur — tek task
 * varken bu zaten fark etmez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterBroadcaster {

    /** Tüm task'ların dinlediği Redis kanalı. */
    public static final String CHANNEL = "waydee:ws";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /** Hedefe abone olan herkese yayın (harita, bölge, aktivite, yapılandırma). */
    public void broadcast(String destination, Map<String, Object> payload) {
        publish(new ClusterEnvelope(destination, null, payload));
    }

    /** Tek kullanıcıya teslim (sohbet). Oturumu hangi task tutuyorsa orada iner. */
    public void sendToUser(String user, String destination, Map<String, Object> payload) {
        publish(new ClusterEnvelope(destination, user, payload));
    }

    private void publish(ClusterEnvelope envelope) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception ex) {
            log.warn("Küme yayını yapılamadı ({}), yerel teslime düşülüyor: {}",
                    envelope.destination(), ex.toString());
            deliverLocally(envelope);
        }
    }

    /**
     * Mesajı <b>bu</b> task'a bağlı oturumlara teslim eder.
     *
     * <p>Normal akışta yalnızca {@link ClusterMessageListener} çağırır; doğrudan
     * çağrılan tek yer, yukarıdaki Redis kesintisi yedeğidir.
     */
    void deliverLocally(ClusterEnvelope envelope) {
        try {
            if (envelope.user() != null) {
                messagingTemplate.convertAndSendToUser(envelope.user(), envelope.destination(), envelope.payload());
            } else {
                messagingTemplate.convertAndSend(envelope.destination(), envelope.payload());
            }
        } catch (Exception ex) {
            log.warn("WebSocket teslimi başarısız ({}): {}", envelope.destination(), ex.toString());
        }
    }
}
