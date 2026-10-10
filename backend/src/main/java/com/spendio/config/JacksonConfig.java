package com.spendio.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * JSON conventions (ARCHITECTURE.md E.1): nulls are omitted from responses, instants
 * serialize as ISO-8601 with {@code Z} instead of epoch timestamps.
 *
 * <p>Spring Boot 4 auto-configures the Jackson 3 ({@code tools.jackson}) {@code JsonMapper};
 * the customizer API and the date features moved from Spring Boot 3 / Jackson 2
 * ({@code Jackson2ObjectMapperBuilderCustomizer}, {@code SerializationFeature}).
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .changeDefaultPropertyInclusion(
                        inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .disable(
                        DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
                        DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }
}
