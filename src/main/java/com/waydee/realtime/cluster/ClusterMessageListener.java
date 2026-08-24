package com.waydee.realtime.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis kanalından gelen teslim emrini <b>bu</b> task'ın bağlı oturumlarına iletir.
 *
 * <p>Her task bu dinleyiciyi çalıştırır; yayınlayan task da dahil. Teslim
 * yalnızca burada yapıldığı için mesaj her istemciye <b>tam olarak bir kez</b>
 * ulaşır ({@link ClusterBroadcaster} açıklamasına bakınız).
 *
 * <p>Bozuk bir mesaj dinleyiciyi düşürmez: hata loglanır ve sonraki mesajlarla
 * devam edilir — tek bir kötü kayıt tüm gerçek zamanlı akışı susturmamalı.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClusterMessageListener implements MessageListener {

    private final ClusterBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            broadcaster.deliverLocally(objectMapper.readValue(body, ClusterEnvelope.class));
        } catch (Exception ex) {
            log.warn("Küme mesajı çözümlenemedi: {}", ex.toString());
        }
    }
}
