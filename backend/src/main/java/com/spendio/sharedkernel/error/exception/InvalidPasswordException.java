package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Password rejected by the password policy - 422. */
public class InvalidPasswordException extends BusinessException {

    public InvalidPasswordException(String message) {
        super(ErrorCode.PASSWORD_POLICY_VIOLATION, message);
    }
}
