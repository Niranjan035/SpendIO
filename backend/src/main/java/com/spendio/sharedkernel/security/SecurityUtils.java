package com.spendio.sharedkernel.security;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Access to the authenticated user from services, auditors and controllers.
 * Anonymous traffic yields {@link Optional#empty()}, never a fabricated id.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Authentication> currentAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Optional<UserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static Optional<Long> currentUserId() {
        return currentPrincipal().map(UserPrincipal::userId);
    }

    /** For endpoints that are always behind authentication; 401 when there is no user. */
    public static Long requireUserId() {
        return currentUserId().orElseThrow(() -> new BusinessException(
                ErrorCode.AUTH_TOKEN_MISSING, "Authentication is required"));
    }

    public static boolean isAuthenticated() {
        return currentUserId().isPresent();
    }
}
