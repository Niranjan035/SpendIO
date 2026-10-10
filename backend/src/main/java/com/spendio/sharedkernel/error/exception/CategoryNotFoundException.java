package com.spendio.sharedkernel.error.exception;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;

public class CategoryNotFoundException extends BusinessException {
  public CategoryNotFoundException(String message) {
    super(ErrorCode.CATEGORY_NOT_FOUND, message);
  }
}