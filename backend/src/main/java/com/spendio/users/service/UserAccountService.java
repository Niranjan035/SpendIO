package com.spendio.users.service;

import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.error.ResourceNotFoundException;
import com.spendio.users.domain.User;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The narrow seam the auth module uses to read and mutate authentication state
 * (ARCHITECTURE.md B.3: no cross-module repository access; D.2 lockout lives here).
 *
 * <p>All persistence of account state - lockout counters, password hashes, verification
 * flags - goes through this service so the users module keeps ownership of its table.
 */
@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final AccountLockoutService accountLockoutService;

    public UserAccountService(UserRepository userRepository, AccountLockoutService accountLockoutService) {
        this.userRepository = userRepository;
        this.accountLockoutService = accountLockoutService;
    }

    /** Login attempt state for an email, empty when no active account matches (D.2). */
    @Transactional(readOnly = true)
    public Optional<LoginStateResponse> loginStateFor(String email) {
        return userRepository
                .findByEmailAndDeletedAtIsNull(User.normaliseEmail(email))
                .map(LoginStateResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<LoginStateResponse> accountState(Long userId) {
        return userRepository.findById(userId).filter(user -> user.getDeletedAt() == null).map(LoginStateResponse::from);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(User.normaliseEmail(email));
    }

    /** Applies one failed attempt and returns the refreshed lockout state (D.2). */
    @Transactional
    public LoginStateResponse recordLoginFailure(Long userId) {
        User user = require(userId);
        accountLockoutService.recordFailure(user);
        userRepository.save(user);
        return LoginStateResponse.from(user);
    }

    @Transactional
    public void recordLoginSuccess(Long userId) {
        User user = require(userId);
        accountLockoutService.recordSuccess(user);
        userRepository.save(user);
    }

    @Transactional
    public void markEmailVerified(Long userId) {
        User user = require(userId);
        user.markEmailVerified();
        userRepository.save(user);
    }

    /** Replaces the password hash and clears the forced-change flag (D.2). */
    @Transactional
    public void changePassword(Long userId, String encodedPassword) {
        User user = require(userId);
        user.changePassword(encodedPassword);
        userRepository.save(user);
    }

    /** Public profile of the account, e.g. for {@code GET /auth/me}. */
    @Transactional(readOnly = true)
    public UserResponse summary(Long userId) {
        return UserResponse.from(require(userId));
    }

    /** Marks the last login time on successful credential verification. */
    @Transactional
    public void recordAuthenticatedLogin(Long userId, Instant at) {
        User user = require(userId);
        user.recordSuccessfulLogin(at);
        userRepository.save(user);
    }

    private User require(Long userId) {
        return userRepository
                .findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "user " + userId));
    }
}
