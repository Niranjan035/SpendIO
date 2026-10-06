package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/** Action attempted on an import that is not in a state that allows it - 409. */
public class ImportNotReadyException extends BusinessException {

    public ImportNotReadyException(String message) {
        super(ErrorCode.IMPORT_NOT_READY, message);
    }
}
