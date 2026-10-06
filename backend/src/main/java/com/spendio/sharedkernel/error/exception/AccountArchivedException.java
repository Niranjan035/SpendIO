package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Write against an archived account - 422. */
public class AccountArchivedException extends BusinessException {

    public AccountArchivedException(String message) {
        super(ErrorCode.ACCOUNT_ARCHIVED, message);
    }
}
