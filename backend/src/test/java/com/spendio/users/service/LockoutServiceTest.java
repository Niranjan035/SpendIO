package com.spendio.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.domain.User;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Progressive per-user lockout (ARCHITECTURE.md D.3, I.4, K.1): five failures locks
 * for fifteen minutes, a successful challenge resets everything.
 */
class LockoutServiceTest {

    private final AccountLockoutService lockoutService = new AccountLockoutService();

    @Test
    void staysOpenBelowTheThreshold() {
        User user = user();

        for (int i = 0; i < AccountLockoutService.MAX_FAILED_ATTEMPTS - 1; i++) {
            lockoutService.recordFailure(user);
        }

        assertThat(user.getFailedLoginAttempts()).isEqualTo(4);
        assertThat(lockoutService.isLocked(user)).isFalse();
    }

    @Test
    void locksAtTheThresholdWithAnExpiry() {
        User user = user();

        for (int i = 0; i < AccountLockoutService.MAX_FAILED_ATTEMPTS; i++) {
            lockoutService.recordFailure(user);
        }

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(lockoutService.isLocked(user)).isTrue();
        assertThat(user.getLockedUntil()).isAfter(Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> lockoutService.assertNotLocked(user))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                    assertThat(((BusinessException) ex).getProperties()).containsKey("lockedUntil");
                });
    }

    @Test
    void successClearsCountersAndLock() {
        User user = user();
        for (int i = 0; i < AccountLockoutService.MAX_FAILED_ATTEMPTS; i++) {
            lockoutService.recordFailure(user);
        }

        lockoutService.recordSuccess(user);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(lockoutService.isLocked(user)).isFalse();
        lockoutService.assertNotLocked(user);
    }

    @Test
    void lockExpiresOnItsOwnAfterTheWindow() {
        User user = user();
        user.recordFailedLogin(1, Instant.now().minusSeconds(1));

        assertThat(user.isLocked(Instant.now())).isFalse();
    }

    private static User user() {
        return User.create("ana@spendio.test", "{bcrypt}$2a$12$abcdefghijklmnopqrstuv", "UTC", "en-GB", "USD");
    }
}
