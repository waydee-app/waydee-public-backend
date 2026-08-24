package com.waydee.realtime;

import com.waydee.common.security.AuthenticatedUser;
import com.waydee.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT çerçevesinde JWT doğrular ve oturuma Principal bağlar.
 * Kimliksiz CONNECT reddedilir; böylece /user/queue/** hedefleri güvenle
 * kullanılabilir ve hiçbir abonelik anonim açılamaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String header = accessor.getFirstNativeHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        AuthenticatedUser user = token != null ? jwtService.parse(token).orElse(null) : null;
        if (user == null) {
            log.debug("WS CONNECT reddedildi: geçersiz ya da eksik token");
            throw new MessagingException("WebSocket için kimlik doğrulaması gerekli");
        }
        accessor.setUser(new StompPrincipal(user.id().toString(), user.username()));
        return message;
    }
}
