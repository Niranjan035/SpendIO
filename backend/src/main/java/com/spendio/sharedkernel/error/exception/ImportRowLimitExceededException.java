package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Import exceeds the configured row cap (default 50 000) - 422. */
public class ImportRowLimitExceededException extends BusinessException {

    public ImportRowLimitExceededException(String message) {
        super(ErrorCode.IMPORT_ROW_LIMIT_EXCEEDED, message);
    }
}
