package com.spendio.fx.support;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the FX provider at startup: an HTTP client when {@code spendio.fx.provider-url}
 * is configured, the no-op client otherwise (ARCHITECTURE.md D.5).
 */
@Configuration
@EnableConfigurationProperties(FxProviderProperties.class)
public class FxProviderConfiguration {

    @Bean
    public FxProviderClient fxProviderClient(FxProviderProperties properties, ObjectMapper objectMapper) {
        if (properties.providerUrl() == null || properties.providerUrl().isBlank()) {
            return new NoopFxProviderClient();
        }
        return new HttpFxProviderClient(properties.effectiveProviderName(), properties.providerUrl(), objectMapper);
    }
}