package com.spendio.fx.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FX provider wiring: {@code spendio.fx.provider-url} enables the HTTP client; when it
 * is absent the no-op client answers and manual rates remain the only source.
 */
@ConfigurationProperties(prefix = "spendio.fx")
public record FxProviderProperties(String providerUrl, String providerName) {

    public String effectiveProviderName() {
        return providerName == null || providerName.isBlank() ? "http" : providerName;
    }
}