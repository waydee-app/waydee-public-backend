package com.waydee.realtime.cluster;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub aboneliği — her task açılışta {@code waydee:ws} kanalını dinler.
 *
 * <p>⚠️ Bu kap <b>kendi thread havuzunda</b> çalışır; Tomcat'in istek
 * thread'lerini kullanmaz. Yani gerçek zamanlı fan-out, HTTP yükünden bağımsızdır.
 */
@Configuration
public class ClusterMessagingConfig {

    @Bean
    public RedisMessageListenerContainer clusterListenerContainer(RedisConnectionFactory connectionFactory,
                                                                  ClusterMessageListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(ClusterBroadcaster.CHANNEL));
        return container;
    }
}
