package com.spendio.users.service;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.domain.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Failed-login lockout bookkeeping (ARCHITECTURE.md D.3 "account lockout service").
 *
 * <p>Phase 1 only owns the counters on the {@code users} row - wiring it into the
 * authentication flow is Phase 2, when the login endpoint exists.
 */
@Service
public class AccountLockoutService {

    /** Five wrong passwords in a row locks the account, mirroring {@code V2}. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** How long the account stays locked after the threshold is reached. */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Transactional
    public void recordFailure(User user) {
        user.recordFailedLogin(MAX_FAILED_ATTEMPTS, Instant.now().plus(LOCK_DURATION));
    }

    @Transactional
    public void recordSuccess(User user) {
        user.recordSuccessfulLogin(Instant.now());
    }

    public boolean isLocked(User user) {
        return user.isLocked(Instant.now());
    }

    @Transactional
    public void assertNotLocked(User user) {
        if (isLocked(user)) {
            throw new BusinessException(
                            ErrorCode.ACCOUNT_LOCKED,
                            "Too many failed attempts; try again later")
                    .withProperty("lockedUntil", user.getLockedUntil());
        }
    }
}
