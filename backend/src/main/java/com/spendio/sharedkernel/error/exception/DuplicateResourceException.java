package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/**
 * Uniqueness violation raised by a service before it hits the DB,
 * carrying the module-specific code (e.g. {@code ACCOUNT_NAME_DUPLICATE}).
 */
public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
