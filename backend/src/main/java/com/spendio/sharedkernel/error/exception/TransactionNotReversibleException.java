package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Reversal attempted twice or on a non-reversible row - 409. */
public class TransactionNotReversibleException extends BusinessException {

    public TransactionNotReversibleException(String message) {
        super(ErrorCode.TRANSACTION_NOT_REVERSIBLE, message);
    }
}
