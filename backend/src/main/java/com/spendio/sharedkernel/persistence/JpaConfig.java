package com.spendio.sharedkernel.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Persistence boundary: entities and repositories are discovered under {@code com.spendio},
 * the schema itself is owned by Flyway (ARCHITECTURE.md C.1).
 */
@Configuration
@EntityScan("com.spendio")
@EnableJpaRepositories(basePackages = "com.spendio")
public class JpaConfig {
}
