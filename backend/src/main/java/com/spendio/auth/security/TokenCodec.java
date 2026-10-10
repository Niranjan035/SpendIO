package com.spendio.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque token minting and hashing for the auth module (ARCHITECTURE.md I.4):
 * 256-bit random value, stored only as SHA-256 hex.
 */
public final class TokenCodec {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenCodec() {
    }

    /** URL-safe opaque token (43 chars) suitable for links and cookies. */
    public static String generate() {
        byte[] value = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** Lowercase SHA-256 hex (64 chars) - the form tokens are persisted in. */
    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JRE", ex);
        }
    }
}
