package com.waydee.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authChannelInterceptor;

    @Value("${waydee.security.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic genel yayınlar, /queue kullanıcıya özel teslim (convertAndSendToUser).
        //
        // ⚠️ Bu broker SÜREÇ İÇİDİR ve öyle kalmalıdır: yalnız BU task'a bağlı
        // oturumlara teslim eder. Task'lar arası dağıtımı ClusterBroadcaster
        // Redis Pub/Sub ile yapar (bulgu K3). Yani "simple broker" olması artık
        // yatay ölçeklemenin önünde DEĞİLDİR — yeter ki yayınlar doğrudan
        // SimpMessagingTemplate'e değil, ClusterBroadcaster'a yazılsın.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // CONNECT'te JWT doğrulanır; kimliksiz oturum açılamaz (chat güvenliğinin temeli).
        registration.interceptors(authChannelInterceptor);
    }
}
