package com.spendio.sharedkernel.error;

/**
 * Missing resource or resource owned by another user - always 404, never 403,
 * so ownership can never be probed (ARCHITECTURE.md D.16, E.16).
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
