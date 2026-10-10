package com.spendio.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings (ARCHITECTURE.md I.4, K.1).
 *
 * <p>The JWT secret comes from the environment, must be at least 32 characters and is
 * redacted from {@code toString()} so it can never reach a log line.
 */
@ConfigurationProperties(prefix = "spendio.security")
public record SecurityProperties(Jwt jwt, Cookie cookie, RateLimit rateLimit, Password password) {

    public SecurityProperties {
        jwt = jwt == null ? Jwt.DEFAULT : jwt;
        cookie = cookie == null ? Cookie.DEFAULT : cookie;
        rateLimit = rateLimit == null ? RateLimit.DEFAULT : rateLimit;
        password = password == null ? Password.DEFAULT : password;
    }

    public record Jwt(
            String secret,
            String kid,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            Duration rememberMeRefreshTtl,
            Duration absoluteSessionTtl) {

        static final Jwt DEFAULT = new Jwt(
                null,
                "v1",
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                Duration.ofDays(90),
                Duration.ofDays(90));

        public Jwt {
            if (kid == null || kid.isBlank()) {
                kid = "v1";
            }
            accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
            refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(30) : refreshTokenTtl;
            rememberMeRefreshTtl =
                    rememberMeRefreshTtl == null ? Duration.ofDays(90) : rememberMeRefreshTtl;
            absoluteSessionTtl =
                    absoluteSessionTtl == null ? Duration.ofDays(90) : absoluteSessionTtl;
            if (secret != null && secret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "spendio.security.jwt.secret must be at least " + MIN_SECRET_LENGTH
                                + " characters (set SPENDIO_JWT_SECRET)");
            }
        }

        public static final int MIN_SECRET_LENGTH = 32;

        /** Never expose the signing key - records would otherwise print it in logs. */
        @Override
        public String toString() {
            return "Jwt[kid=" + kid
                    + ", accessTokenTtl=" + accessTokenTtl
                    + ", refreshTokenTtl=" + refreshTokenTtl
                    + ", rememberMeRefreshTtl=" + rememberMeRefreshTtl
                    + ", absoluteSessionTtl=" + absoluteSessionTtl
                    + ", secret=***]";
        }
    }

    public record Cookie(String name, String path, boolean secure, boolean sameSiteStrict) {

        static final Cookie DEFAULT = new Cookie("spendio_rt", "/api/v1/auth", false, true);

        public Cookie {
            if (name == null || name.isBlank()) {
                name = "spendio_rt";
            }
            if (path == null || path.isBlank()) {
                path = "/api/v1/auth";
            }
        }
    }

    public record RateLimit(Duration window, int sensitiveRequests, int authRequests) {

        static final RateLimit DEFAULT = new RateLimit(Duration.ofMinutes(5), 10, 60);

        public RateLimit {
            window = window == null ? Duration.ofMinutes(5) : window;
            if (sensitiveRequests <= 0) {
                sensitiveRequests = 10;
            }
            if (authRequests <= 0) {
                authRequests = 60;
            }
        }
    }

    public record Password(boolean breachedCheckEnabled, int minLength, int maxLength) {

        static final Password DEFAULT = new Password(false, 10, 72);

        public Password {
            if (minLength <= 0) {
                minLength = 10;
            }
            // BCrypt hashes only the first 72 bytes; anything longer would be silently truncated.
            if (maxLength < minLength || maxLength > 72) {
                maxLength = 72;
            }
        }
    }
}
