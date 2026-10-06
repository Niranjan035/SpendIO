package com.spendio.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cross-cutting meter tagging (ARCHITECTURE.md A.6). Per-endpoint HTTP timers come from
 * Actuator's {@code http.server.requests}; module counters (imports, generated
 * transactions, lockouts) register where they fire.
 */
@Configuration
public class MetricsConfig {

    @Bean
    MeterFilter applicationTagFilter() {
        return MeterFilter.commonTags(List.of(Tag.of("application", "spendio")));
    }
}
