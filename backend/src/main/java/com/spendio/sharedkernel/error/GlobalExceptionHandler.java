package com.spendio.sharedkernel.error;

import com.spendio.sharedkernel.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to RFC-9457 {@link ProblemDetail} with a stable {@code code},
 * {@code fieldErrors} and {@code traceId} (ARCHITECTURE.md A.2, D.16).
 *
 * <p>4xx is logged at INFO without a stack trace, 5xx at ERROR with one. Stack traces,
 * SQL and internal messages are never sent to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String FIELD_ERRORS = "fieldErrors";
    private static final String TRACE_ID = "traceId";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        ProblemDetail body = problemDetail(errorCode, ex.getMessage(), request);
        ex.getProperties().forEach(body::setProperty);
        body.setProperty(FIELD_ERRORS, ex.getFieldErrors());

        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.status());
        if (ex instanceof TooManyRequestsException tooMany && tooMany.getRetryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(tooMany.getRetryAfterSeconds()));
        }
        logOutcome(errorCode.statusCode(), errorCode.name(), request, ex, null);
        return response.body(body);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleValidation(BindException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        for (org.springframework.validation.FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(FieldError.of(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            fieldErrors.add(FieldError.of(globalError.getObjectName(), globalError.getDefaultMessage()));
        }
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = new ArrayList<>();
        ex.getConstraintViolations().forEach(violation ->
                fieldErrors.add(FieldError.of(violation.getPropertyPath().toString(), violation.getMessage())));
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors =
                List.of(FieldError.of(ex.getName(), "is not a valid value"));
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors =
                List.of(FieldError.of(ex.getHeaderName(), "is required"));
        return validationResponse(fieldErrors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return simple(ErrorCode.MALFORMED_REQUEST, "Request body is not readable", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return simple(ErrorCode.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return simple(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type is not supported", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ProblemDetail> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return simple(ErrorCode.NOT_ACCEPTABLE, "Response type is not acceptable", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return simple(ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the size limit", request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipart(MultipartException ex, HttpServletRequest request) {
        return simple(ErrorCode.MALFORMED_REQUEST, "Malformed multipart request", request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> handleNoHandler(Exception ex, HttpServletRequest request) {
        return simple(ErrorCode.RESOURCE_NOT_FOUND, "No resource at " + request.getRequestURI(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        logOutcome(409, ErrorCode.CONFLICT.name(), request, ex, null);
        return ResponseEntity.status(409)
                .body(problemDetail(ErrorCode.CONFLICT, "The request conflicts with existing data", request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        return simple(ErrorCode.CONFLICT, "The resource was modified by another request", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return simple(ErrorCode.FORBIDDEN, "You do not have access to this resource", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        return simple(ErrorCode.UNAUTHORIZED, "Authentication is required", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        logOutcome(500, ErrorCode.INTERNAL_ERROR.name(), request, null, ex);
        return ResponseEntity.status(500).body(problemDetail(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request));
    }

    private ResponseEntity<ProblemDetail> validationResponse(List<FieldError> fieldErrors, HttpServletRequest request) {
        ProblemDetail body = problemDetail(ErrorCode.VALIDATION_ERROR, "Request validation failed", request);
        body.setProperty(FIELD_ERRORS, fieldErrors);
        logOutcome(400, ErrorCode.VALIDATION_ERROR.name(), request, null, null);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<ProblemDetail> simple(ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail body = problemDetail(errorCode, detail, request);
        logOutcome(errorCode.statusCode(), errorCode.name(), request, null, null);
        return ResponseEntity.status(errorCode.status()).body(body);
    }

    public static ProblemDetail problemDetail(ErrorCode errorCode, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatus(errorCode.status());
        body.setType(URI.create(errorCode.type()));
        body.setTitle(errorCode.title());
        body.setDetail(detail);
        body.setInstance(safeUri(request == null ? null : request.getRequestURI()));
        body.setProperty("code", errorCode.name());
        body.setProperty(FIELD_ERRORS, List.of());
        String traceId = MDC.get(RequestIdFilter.MDC_KEY);
        if (traceId != null) {
            body.setProperty(TRACE_ID, traceId);
        }
        return body;
    }

    private static URI safeUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void logOutcome(int status, String code, HttpServletRequest request, Throwable ex, Throwable unexpected) {
        String method = request == null ? "-" : request.getMethod();
        String uri = request == null ? "-" : request.getRequestURI();
        if (unexpected != null || status >= 500) {
            log.error("{} {} -> {} ({}) [traceId={}]", method, uri, status, code,
                    MDC.get(RequestIdFilter.MDC_KEY), unexpected != null ? unexpected : ex);
        } else {
            log.info("{} {} -> {} ({}) [traceId={}]", method, uri, status, code, MDC.get(RequestIdFilter.MDC_KEY));
        }
    }
}
