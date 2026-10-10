package com.spendio.auth.security;

import com.spendio.config.SecurityProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Have-I-Been-Pwned k-anonymity range check (ARCHITECTURE.md D.2, K.1 breach-list
 * check). Only the 5-character SHA-1 prefix ever leaves the process.
 *
 * <p>Fails open: a network problem logs a warning and admits the password - a
 * dependency outage must not block sign-up (E.16 503 rule for degraded deps).
 */
@Service
public class BreachedPasswordChecker {

    private static final Logger log = LoggerFactory.getLogger(BreachedPasswordChecker.class);
    private static final String RANGE_URL = "https://api.pwnedpasswords.com/range/";

    private final HttpClient httpClient;
    private final boolean enabled;

    @Autowired
    public BreachedPasswordChecker(SecurityProperties properties) {
        this(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                properties.password().breachedCheckEnabled());
    }

    BreachedPasswordChecker(HttpClient httpClient, boolean enabled) {
        this.httpClient = httpClient;
        this.enabled = enabled;
    }

    public boolean isBreached(String rawPassword) {
        if (!enabled) {
            return false;
        }
        try {
            String sha1 = sha1Hex(rawPassword).toUpperCase(Locale.ROOT);
            String prefix = sha1.substring(0, 5);
            String suffix = sha1.substring(5);
            HttpRequest request = HttpRequest.newBuilder(URI.create(RANGE_URL + prefix))
                    .timeout(Duration.ofSeconds(3))
                    .header("Add-Padding", "true")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("HIBP range lookup returned {}", response.statusCode());
                return false;
            }
            for (String line : response.body().split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.regionMatches(true, 0, suffix, 0, colon)) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            log.warn("HIBP range lookup unavailable, allowing password: {}", ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is required by the JRE", ex);
        }
    }
}
