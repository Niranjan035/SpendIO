package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Currency absent from the reference table or inactive - 422. */
public class CurrencyNotSupportedException extends BusinessException {

    public CurrencyNotSupportedException(String message) {
        super(ErrorCode.CURRENCY_NOT_SUPPORTED, message);
    }
}
