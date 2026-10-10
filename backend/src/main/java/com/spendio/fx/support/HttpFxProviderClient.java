package com.spendio.fx.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP FX provider client (ARCHITECTURE.md D.5): 3s read/connect timeout, a single
 * retry, and a circuit-breaker-lite that skips calling out while failures are
 * piling up so a dead provider cannot stall the daily refresh job.
 *
 * <p>Request shape targets the Frankfurter v1 reference-rate service configured by
 * default: {@code GET {baseUrl}/{date}?base=USD}, e.g.
 * {@code https://api.frankfurter.dev/v1/2026-10-09?base=USD}. Expected payload:
 * {@code {"base":"USD","date":"2026-10-09","rates":{"EUR":0.9,...}}} - one rate per
 * quote currency against the requested base.
 */
public class HttpFxProviderClient implements FxProviderClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFxProviderClient.class);

    private static final int CONSECUTIVE_FAILURE_LIMIT = 5;
    private static final Duration COOLDOWN = Duration.ofSeconds(60);

    private final String providerName;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private int consecutiveFailures;
    private Instant openUntil = Instant.EPOCH;

    public HttpFxProviderClient(String providerName, String baseUrl, ObjectMapper objectMapper) {
        this.providerName = providerName;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return providerName;
    }

    @Override
    public Map<String, BigDecimal> fetch(String base, LocalDate date) {
        if (Instant.now().isBefore(openUntil)) {
            log.debug("fx provider circuit open - skipping {} {}", base, date);
            return Map.of();
        }
        String path = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        URI uri = URI.create(path + date + "?base=" + base);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String body = restClient.get().uri(uri).retrieve().body(String.class);
                Map<String, BigDecimal> rates = parse(body);
                consecutiveFailures = 0;
                return rates;
            } catch (Exception ex) {
                last = ex;
                log.debug("fx provider attempt {} failed: {}", attempt + 1, ex.getMessage());
            }
        }
        recordFailure();
        log.warn("fx provider unavailable for {} {}: {}", base, date, last == null ? "unknown" : last.getMessage());
        return Map.of();
    }

    private void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= CONSECUTIVE_FAILURE_LIMIT) {
            openUntil = Instant.now().plus(COOLDOWN);
            consecutiveFailures = 0;
            log.warn("fx provider circuit open for {}", COOLDOWN);
        }
    }

    private Map<String, BigDecimal> parse(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode rates = root.has("rates") ? root.get("rates") : root;
        Map<String, BigDecimal> parsed = new LinkedHashMap<>();
        rates.properties().forEach(entry -> {
            if (entry.getValue().isNumber()) {
                parsed.put(entry.getKey().toUpperCase(), entry.getValue().decimalValue());
            }
        });
        return parsed;
    }
}