package com.spendio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound mail settings ({@code spendio.mail.*}, ARCHITECTURE.md D.13).
 *
 * <p>{@code enabled=false} (the default outside prod/dev) turns {@code MailService}
 * into a logging no-op so no SMTP connection is ever attempted in tests or CI.
 */
@ConfigurationProperties(prefix = "spendio.mail")
public record MailProperties(boolean enabled, String from, String baseUrl) {

    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "SpendIO <no-reply@spendio.local>";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:5173";
        }
    }
}
