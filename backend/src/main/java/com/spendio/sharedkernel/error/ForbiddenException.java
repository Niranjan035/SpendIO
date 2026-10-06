package com.spendio.sharedkernel.error;

/** Authenticated but not permitted - 403 (never used for ownership misses). */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
