package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Category still referenced by transactions/budgets - 409. */
public class CategoryInUseException extends BusinessException {

    public CategoryInUseException(String message) {
        super(ErrorCode.CATEGORY_IN_USE, message);
    }
}
