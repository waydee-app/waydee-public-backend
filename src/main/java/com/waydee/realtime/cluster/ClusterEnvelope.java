package com.waydee.realtime.cluster;

import java.util.Map;

/**
 * Örnekler (task'lar) arasında dolaşan WebSocket teslim emri.
 *
 * @param destination STOMP hedefi — genel yayında {@code /topic/...},
 *                    kullanıcıya özel teslimde {@code /queue/...}
 * @param user        yalnız kullanıcıya özel teslimde dolu; {@code null} ise
 *                    hedefe abone olan <b>herkese</b> gider
 * @param payload     istemciye gidecek gövde (JSON'a çevrilebilir olmalı)
 */
public record ClusterEnvelope(String destination, String user, Map<String, Object> payload) {
}
