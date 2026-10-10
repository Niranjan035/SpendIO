package com.spendio.sharedkernel.security;

import tools.jackson.databind.ObjectMapper;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.error.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

/**
 * 403 responses in the shared ProblemDetail shape. Also used by {@code CsrfFilter},
 * so a missing {@code X-XSRF-TOKEN} is reported as {@code CSRF_TOKEN_INVALID}.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode errorCode = isCsrfFailure(accessDeniedException)
                ? ErrorCode.CSRF_TOKEN_INVALID
                : ErrorCode.FORBIDDEN;

        ProblemDetail body = GlobalExceptionHandler.problemDetail(errorCode, errorCode.title(), request);

        response.setStatus(errorCode.statusCode());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static boolean isCsrfFailure(AccessDeniedException exception) {
        return exception instanceof MissingCsrfTokenException || exception instanceof InvalidCsrfTokenException;
    }
}
