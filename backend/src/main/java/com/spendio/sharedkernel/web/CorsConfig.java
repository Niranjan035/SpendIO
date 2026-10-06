package com.spendio.sharedkernel.web;

import com.spendio.config.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS allowlist wired into the security chain (ARCHITECTURE.md A.2, D.16).
 * Refuses to start if credentials are allowed together with a wildcard origin.
 */
@Configuration
@EnableConfigurationProperties(WebProperties.class)
public class CorsConfig {

    public static final String CORS_SOURCE_BEAN = "corsConfigurationSource";

    @Bean(CORS_SOURCE_BEAN)
    CorsConfigurationSource corsConfigurationSource(WebProperties webProperties) {
        WebProperties.Cors settings = webProperties.cors();
        if (settings == null) {
            throw new IllegalStateException("spendio.web.cors must be configured");
        }
        if (settings.allowCredentials()
                && (settings.allowedOrigins().contains("*") || settings.allowedOrigins().isEmpty())) {
            throw new IllegalStateException(
                    "spendio.web.cors.allowed-origins must be an explicit allowlist when credentials are allowed");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(settings.allowedOrigins());
        configuration.setAllowedMethods(settings.allowedMethods());
        configuration.setAllowedHeaders(settings.allowedHeaders());
        configuration.setAllowCredentials(settings.allowCredentials());
        configuration.setMaxAge(settings.maxAge());
        configuration.addAllowedHeader(RequestIdFilter.REQUEST_ID_HEADER);
        configuration.addAllowedHeader(HttpHeaders.AUTHORIZATION);
        configuration.addAllowedHeader("X-XSRF-TOKEN");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
