package com.spendio.fx.support;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the wire contract of {@link HttpFxProviderClient} against a throwaway
 * socket HTTP server: the Frankfurter v1 request shape ({@code {date}?base=USD}),
 * payload parsing and the empty-map fallback on a failing provider.
 */
class HttpFxProviderClientTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private ServerSocket server;
    private volatile String requestLine;

    @AfterEach
    void tearDown() throws Exception {
        running.set(false);
        if (server != null) {
            server.close();
        }
        executor.shutdownNow();
    }

    @Test
    void fetchUsesTheFrankfurterPathShapeAndParsesOneRatePerQuote() throws Exception {
        HttpFxProviderClient client = startResponding(200,
                "{\"base\":\"USD\",\"date\":\"2026-10-09\",\"rates\":{\"INR\":83.2,\"EUR\":0.9}}");

        Map<String, BigDecimal> rates = client.fetch("USD", LocalDate.of(2026, 10, 9));

        assertThat(requestLine).isEqualTo("GET /v1/2026-10-09?base=USD HTTP/1.1");
        assertThat(rates).containsEntry("INR", new BigDecimal("83.2"));
        assertThat(rates).containsEntry("EUR", new BigDecimal("0.9"));
    }

    @Test
    void fetchReturnsEmptyMapWhenTheProviderFails() throws Exception {
        HttpFxProviderClient client = startResponding(500, "{}");

        Map<String, BigDecimal> rates = client.fetch("USD", LocalDate.of(2026, 10, 9));

        assertThat(rates).isEmpty();
        assertThat(requestLine).isEqualTo("GET /v1/2026-10-09?base=USD HTTP/1.1");
    }

    private HttpFxProviderClient startResponding(int statusCode, String body) throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        executor.submit(() -> {
            while (running.get()) {
                try (Socket client = server.accept()) {
                    requestLine = readRequestLine(client);
                    respond(client, statusCode, body);
                } catch (Exception ignored) {
                    // closed on teardown
                }
            }
        });
        return new HttpFxProviderClient("test-provider",
                "http://127.0.0.1:" + server.getLocalPort() + "/v1", new ObjectMapper());
    }

    private static String readRequestLine(Socket client) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        String line = reader.readLine();
        String header;
        while ((header = reader.readLine()) != null && !header.isEmpty()) {
            // drain the request head so the client can read our response
        }
        return line;
    }

    private static void respond(Socket client, int statusCode, String body) throws Exception {
        String reason = statusCode == 200 ? " OK" : " Internal Server Error";
        String response = "HTTP/1.1 " + statusCode + reason + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + body.length() + "\r\n"
                + "Connection: close\r\n\r\n"
                + body;
        OutputStream out = client.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}