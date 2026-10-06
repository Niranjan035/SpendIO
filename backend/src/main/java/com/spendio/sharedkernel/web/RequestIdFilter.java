package com.spendio.sharedkernel.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts / propagates {@code X-Request-Id}, puts it in the MDC and on the response
 * (ARCHITECTURE.md D.16, E.1 - every response carries the header).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final Pattern ACCEPTABLE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(REQUEST_ID_HEADER));
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** A safe incoming id is reused so proxies and clients can correlate; otherwise one is generated. */
    private String resolve(String incoming) {
        if (incoming != null && ACCEPTABLE.matcher(incoming).matches()) {
            return incoming;
        }
        return newRequestId();
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
