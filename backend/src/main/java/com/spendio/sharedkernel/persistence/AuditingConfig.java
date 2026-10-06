package com.spendio.sharedkernel.persistence;

import com.spendio.sharedkernel.security.SecurityUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing: {@code created_by}/{@code updated_by} come from the security context,
 * timestamps are UTC truncated to milliseconds so API responses match the stored
 * {@code DATETIME(3)} values exactly (ARCHITECTURE.md D.16).
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class AuditingConfig {

    @Bean("auditorAware")
    AuditorAware<Long> auditorAware() {
        return () -> SecurityUtils.currentUserId();
    }

    @Bean("utcDateTimeProvider")
    DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
