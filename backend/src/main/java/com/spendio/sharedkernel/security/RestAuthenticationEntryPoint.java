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
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 responses for unauthenticated access, in the same ProblemDetail shape as
 * every other error (ARCHITECTURE.md E.1).
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        // The JWT filter records why a bearer token was rejected (expired vs invalid).
        Object tokenError = request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        ErrorCode errorCode;
        if (tokenError instanceof ErrorCode recorded) {
            errorCode = recorded;
        } else if (authenticationException == null
                || authenticationException instanceof InsufficientAuthenticationException) {
            errorCode = ErrorCode.AUTH_TOKEN_MISSING;
        } else {
            errorCode = ErrorCode.AUTH_TOKEN_INVALID;
        }

        write(errorCode, request, response);
    }

    private void write(ErrorCode errorCode, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ProblemDetail body = GlobalExceptionHandler.problemDetail(errorCode, errorCode.title(), request);
        response.setStatus(errorCode.statusCode());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
