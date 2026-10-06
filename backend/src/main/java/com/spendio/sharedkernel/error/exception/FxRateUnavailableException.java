package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

/**
 * No resolvable FX rate for a conversion - 503, never fails an unrelated write
 * (ARCHITECTURE.md A.4, D.5).
 */
public class FxRateUnavailableException extends BusinessException {

    public FxRateUnavailableException(String message) {
        super(ErrorCode.FX_RATE_UNAVAILABLE, message);
    }
}
