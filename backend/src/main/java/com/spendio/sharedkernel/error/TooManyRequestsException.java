package com.spendio.sharedkernel.error;

/**
 * Client-side rate limit - 429 with a {@code Retry-After} header
 * (ARCHITECTURE.md E.1, D.16).
 */
public class TooManyRequestsException extends BusinessException {

    private final Long retryAfterSeconds;

    public TooManyRequestsException(String message) {
        this(message, null);
    }

    public TooManyRequestsException(String message, Long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, message);
        this.retryAfterSeconds = retryAfterSeconds;
        if (retryAfterSeconds != null) {
            withProperty("retryAfterSeconds", retryAfterSeconds);
        }
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
