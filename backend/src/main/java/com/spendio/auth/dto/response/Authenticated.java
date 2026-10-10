package com.spendio.auth.dto.response;

import java.time.Duration;

/** An authentication outcome: body to return plus the raw refresh token for the cookie. */
public record Authenticated(AuthResponse auth, String rawRefreshToken, Duration refreshCookieTtl) {}
