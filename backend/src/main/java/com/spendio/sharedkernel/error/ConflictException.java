package com.spendio.sharedkernel.error;

/** Duplicate / concurrent-modification conflicts - 409. */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
