package com.spendio.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Runs a test as an authenticated user: {@code @WithAuthenticatedUser(42L)} puts a
 * {@code UserPrincipal} for id 42 into the security context, which is what
 * {@code @CurrentUserId} and the auditors read.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WithSecurityContext(factory = WithAuthenticatedUserSecurityContextFactory.class)
public @interface WithAuthenticatedUser {

    /** User id to authenticate as. */
    long value() default 1L;
}
