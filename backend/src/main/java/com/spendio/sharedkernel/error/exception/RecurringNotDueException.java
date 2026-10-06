package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** {@code run-now} on an occurrence that is not due yet - 422. */
public class RecurringNotDueException extends BusinessException {

    public RecurringNotDueException(String message) {
        super(ErrorCode.RECURRING_NOT_DUE, message);
    }
}
