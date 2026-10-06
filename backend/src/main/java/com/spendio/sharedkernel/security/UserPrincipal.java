package com.spendio.sharedkernel.security;

import java.security.Principal;

/**
 * Authenticated user identity carried by the {@code Authentication} principal.
 * Populated by the JWT filter (Phase 2); kept free of role/authority concepts for now.
 */
public record UserPrincipal(Long userId, String email, boolean emailVerified) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
