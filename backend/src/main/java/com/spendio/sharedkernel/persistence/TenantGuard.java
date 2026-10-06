package com.spendio.sharedkernel.persistence;

import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.error.ResourceNotFoundException;
import com.spendio.sharedkernel.security.SecurityUtils;
import org.springframework.stereotype.Component;

/**
 * Row-level ownership assertions (ARCHITECTURE.md D.16): a row that belongs to someone
 * else must surface as 404, never 403, otherwise ownership becomes observable.
 */
@Component
public class TenantGuard {

    /** @return the entity when present; raises a 404 otherwise. */
    public <T> T requireFound(T entity, ErrorCode errorCode, String message) {
        if (entity == null) {
            throw new ResourceNotFoundException(errorCode, message);
        }
        return entity;
    }

    /** Asserts the row belongs to the authenticated user; raises a 404 when it does not. */
    public void requireOwned(Long ownerUserId, ErrorCode errorCode, String message) {
        requireOwned(ownerUserId, SecurityUtils.currentUserId().orElse(null), errorCode, message);
    }

    /** Asserts an explicit owner id matches the given user id (for batch/idempotent paths). */
    public void requireOwned(Long ownerUserId, Long userId, ErrorCode errorCode, String message) {
        if (ownerUserId == null || !ownerUserId.equals(userId)) {
            throw new ResourceNotFoundException(errorCode, message);
        }
    }
}
