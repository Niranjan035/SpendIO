package com.spendio.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web layer settings - the CORS allowlist is configuration, never a wildcard,
 * because credentials are allowed (ARCHITECTURE.md C.2, A.2).
 */
@ConfigurationProperties(prefix = "spendio.web")
public record WebProperties(Cors cors) {

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders,
            boolean allowCredentials,
            long maxAge) {

        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            allowedMethods = allowedMethods == null ? List.of() : List.copyOf(allowedMethods);
            allowedHeaders = allowedHeaders == null ? List.of() : List.copyOf(allowedHeaders);
        }
    }
}
