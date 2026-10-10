package com.spendio.sharedkernel.security;

import java.security.Principal;
import java.util.UUID;

/**
 * Authenticated user identity carried by the {@code Authentication} principal.
 * Populated by {@link JwtAuthenticationFilter}; kept free of role/authority concepts
 * because every request is a normal user and authorization is ownership checks
 * in the service layer (ARCHITECTURE.md I.4).
 */
public record UserPrincipal(Long userId, String email, boolean emailVerified, UUID sessionId) implements Principal {

    /** Test/support constructor for a principal without a backing session. */
    public UserPrincipal(Long userId, String email, boolean emailVerified) {
        this(userId, email, emailVerified, null);
    }

    @Override
    public String getName() {
        return email;
    }
}
