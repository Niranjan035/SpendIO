package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

public class CategoryTypeMismatchException extends BusinessException {
  public CategoryTypeMismatchException(String message) {
    super(ErrorCode.CATEGORY_IN_USE, message); // treat as appropriate; keep close to 422 semantics but existing codes used
  }
}