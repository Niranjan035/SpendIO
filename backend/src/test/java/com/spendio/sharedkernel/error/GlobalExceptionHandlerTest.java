package com.spendio.sharedkernel.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendio.sharedkernel.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every exception maps to RFC-9457 ProblemDetail with a stable {@code code}
 * (ARCHITECTURE.md A.2, D.16): field errors for input problems, no internal messages
 * in 5xx, correlation id echoed as {@code traceId}.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionBecomes404ProblemDetail() throws Exception {
        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type")
                        .value("https://docs.spendio.app/errors/RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Widget not found"))
                .andExpect(jsonPath("$.instance").value("/probe/business"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void businessExceptionCarriesFieldErrors() throws Exception {
        mockMvc.perform(get("/probe/business-with-fields"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
    }

    @Test
    void constraintViolationBecomesValidationError() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());
    }

    @Test
    void malformedJsonBecomesMalformedRequest() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void typeMismatchBecomesValidationError() throws Exception {
        mockMvc.perform(get("/probe/num?id=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void missingHeaderBecomesValidationError() throws Exception {
        mockMvc.perform(get("/probe/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("X-Required"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("is required"));
    }

    @Test
    void unsupportedMethodBecomes405() throws Exception {
        mockMvc.perform(put("/probe/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedAcceptBecomes406() throws Exception {
        mockMvc.perform(get("/probe/pojo").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void unsupportedContentTypeBecomes415() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void optimisticLockBecomes409() throws Exception {
        mockMvc.perform(get("/probe/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("The resource was modified by another request"));
    }

    @Test
    void rateLimitCarriesRetryAfter() throws Exception {
        mockMvc.perform(get("/probe/rate"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(30));
    }

    @Test
    void unexpectedErrorNeverLeaksInternalDetails() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret database detail"))));
    }

    @Test
    void problemDetailCarriesTheMdcTraceId() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "trace-123");
        try {
            mockMvc.perform(get("/probe/business"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.traceId").value("trace-123"));
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/business")
        ResponseEntity<Void> business() {
            throw new ResourceNotFoundException("Widget not found");
        }

        @GetMapping("/probe/business-with-fields")
        ResponseEntity<Void> businessWithFields() {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY, "Check the input")
                    .withFieldError("name", "must not be blank");
        }

        @PostMapping("/probe/validated")
        ResponseEntity<Void> validated(@Valid @RequestBody Payload payload) {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/probe/body")
        ResponseEntity<Void> body(@RequestBody Payload payload) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/probe/num")
        ResponseEntity<Void> num(@RequestParam("id") Long id) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/probe/header")
        ResponseEntity<Void> header(@RequestHeader("X-Required") String value) {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/probe/pojo")
        Map<String, String> pojo() {
            return Map.of("answer", "42");
        }

        @GetMapping("/probe/lock")
        ResponseEntity<Void> lock() {
            throw new OptimisticLockingFailureException("row was updated");
        }

        @GetMapping("/probe/rate")
        ResponseEntity<Void> rate() {
            throw new TooManyRequestsException("Too many attempts", 30L);
        }

        @GetMapping("/probe/boom")
        ResponseEntity<Void> boom() {
            throw new IllegalStateException("secret database detail");
        }
    }

    record Payload(@NotBlank String name) {
    }
}
