package com.spendio.support;

import com.spendio.sharedkernel.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.List;

/** Builds the security context for {@link WithAuthenticatedUser}. */
class WithAuthenticatedUserSecurityContextFactory implements WithSecurityContextFactory<WithAuthenticatedUser> {

    @Override
    public SecurityContext createSecurityContext(WithAuthenticatedUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Long userId = annotation.value();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user-" + userId + "@spendio.test", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        return context;
    }
}
