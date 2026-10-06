package com.spendio.sharedkernel.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base for all errors that map to a known {@link ErrorCode} and HTTP status.
 *
 * <p>Services throw subclasses; {@link GlobalExceptionHandler} renders the ProblemDetail.
 * Messages are written for end users (they appear verbatim in {@code detail}).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<FieldError> fieldErrors = new ArrayList<>();

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Extra ProblemDetail properties (e.g. {@code lockedUntil}); rendered as-is. */
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public List<FieldError> getFieldErrors() {
        return Collections.unmodifiableList(fieldErrors);
    }

    public BusinessException withProperty(String name, Object value) {
        properties.put(name, value);
        return this;
    }

    public BusinessException withFieldError(String field, String message) {
        fieldErrors.add(FieldError.of(field, message));
        return this;
    }
}
