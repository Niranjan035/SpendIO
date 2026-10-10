package com.spendio.sharedkernel.security;

import com.spendio.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the short-lived HS256 access token (ARCHITECTURE.md I.4, K.1).
 *
 * <p>Tokens carry {@code sub} (user id), {@code email}, {@code emailVerified} and
 * {@code sid} (session id), are typed {@code access} and stamped with the configured
 * {@code kid} so the signing key can be rotated. Refresh tokens never come through here:
 * they are opaque random strings kept in an httpOnly cookie.
 */
@Service
public class JwtService {

    static final String TOKEN_TYPE = "access";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_EMAIL_VERIFIED = "emailVerified";
    static final String CLAIM_SESSION_ID = "sid";

    private final SecurityProperties.Jwt properties;
    private final SecretKey key;

    @Autowired
    public JwtService(SecurityProperties properties) {
        this(properties, properties.jwt());
    }

    JwtService(SecurityProperties properties, SecurityProperties.Jwt jwt) {
        this.properties = jwt;
        byte[] secretBytes = jwt.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < SecurityProperties.Jwt.MIN_SECRET_LENGTH) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String issueAccessToken(Long userId, String email, boolean emailVerified, UUID sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_EMAIL_VERIFIED, emailVerified)
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .header()
                .keyId(properties.kid())
                .type(TOKEN_TYPE)
                .and()
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and validates signature, expiry, {@code typ} and {@code kid}.
     *
     * @throws JwtException when the token is not acceptable - the caller decides how to answer
     */
    public AccessToken parseAccessToken(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        if (!TOKEN_TYPE.equals(jws.getHeader().getType())
                || !properties.kid().equals(jws.getHeader().getKeyId())) {
            throw new JwtException("Unexpected token header");
        }
        Claims claims = jws.getPayload();
        if (claims.getSubject() == null || claims.getId() == null) {
            throw new JwtException("Token is missing required claims");
        }
        return new AccessToken(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Boolean.TRUE.equals(claims.get(CLAIM_EMAIL_VERIFIED, Boolean.class)),
                UUID.fromString(claims.get(CLAIM_SESSION_ID, String.class)),
                claims.getExpiration().toInstant());
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    /** Validated identity carried by a verified access token. */
    public record AccessToken(Long userId, String email, boolean emailVerified, UUID sessionId, Instant expiresAt) {}
}
