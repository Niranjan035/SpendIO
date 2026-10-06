package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.Instant;

/** Too many failed logins - 423, carrying {@code lockedUntil} in the ProblemDetail. */
public class AccountLockedException extends BusinessException {

    public AccountLockedException(String message, Instant lockedUntil) {
        super(ErrorCode.ACCOUNT_LOCKED, message);
        withProperty("lockedUntil", lockedUntil);
    }
}
