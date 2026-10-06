package com.spendio.sharedkernel.error;

/** Semantically invalid request (business rule) - 422. */
public class UnprocessableEntityException extends BusinessException {

    public UnprocessableEntityException(String message) {
        super(ErrorCode.UNPROCESSABLE_ENTITY, message);
    }

    public UnprocessableEntityException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
