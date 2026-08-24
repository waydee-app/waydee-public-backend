package com.waydee.common.security;

import java.util.UUID;

/**
 * JWT'den çözülen, request kapsamında geçerli kimlik. DB erişimi gerektirmez.
 */
public record AuthenticatedUser(UUID id, String username, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
