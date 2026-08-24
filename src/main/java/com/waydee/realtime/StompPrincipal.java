package com.waydee.realtime;

import java.security.Principal;

/**
 * STOMP oturumunun kimliği. {@link #getName()} kullanıcı id'sini döndürür —
 * user-destination yönlendirmesi (convertAndSendToUser) bu ada göre çalışır.
 */
public record StompPrincipal(String userId, String username) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
