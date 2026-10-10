package com.spendio.auth.service;

import com.spendio.auth.dto.request.LoginRequest;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * POST /auth/login (ARCHITECTURE.md D.2, I.4): enumeration-safe generic failure with a
 * constant-time dummy hash, lockout check, credential verification with encoding
 * upgrade, then a fresh session.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final TokenService tokenService;

    /** Verified against for unknown addresses so timing cannot probe the user table. */
    private final String dummyHash;

    public LoginService(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            TokenService tokenService) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Deliberately not {@code @Transactional}: the failure paths must persist their
     * side effects (failed-attempt counters, lockout) even though the method then
     * throws - a rollback would erase the lockout and make brute force free.
     */
    public Authenticated login(LoginRequest request, String ipAddress, String userAgent) {
        Optional<LoginStateResponse> maybe =
                userAccountService.loginStateFor(request.email());

        if (maybe.isEmpty()) {
            // Burn one BCrypt verification so unknown-user and wrong-password cost the same.
            passwordEncoder.matches(request.password(), dummyHash);
            log.info("security event=LOGIN_FAILED reason=unknown_email");
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        LoginStateResponse state = maybe.get();
        if (!state.active()) {
            log.info("security event=LOGIN_FAILED user_id={} reason=disabled", state.user().id());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account disabled");
        }
        Instant now = Instant.now();
        if (state.lockedUntil() != null && now.isBefore(state.lockedUntil())) {
            throw locked(state.lockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), state.passwordHash())) {
            Long userId = state.user().id();
            LoginStateResponse after = userAccountService.recordLoginFailure(userId);
            log.info("security event=LOGIN_FAILED user_id={} attempts={}", userId, after.failedLoginAttempts());
            if (after.lockedUntil() != null && Instant.now().isBefore(after.lockedUntil())) {
                throw locked(after.lockedUntil());
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        Long userId = state.user().id();
        if (passwordEncoder.upgradeEncoding(state.passwordHash())) {
            userAccountService.changePassword(userId, passwordEncoder.encode(request.password()));
        }
        userAccountService.recordLoginSuccess(userId);

        UserResponse user = userAccountService.summary(userId);
        SessionService.IssuedSession issued = sessionService.createSession(
                userId, Boolean.TRUE.equals(request.rememberDevice()), ipAddress, userAgent);
        log.info("security event=LOGIN_SUCCESS user_id={} session={}", userId, issued.session().getSessionUuid());

        AuthResponse auth = tokenService.issueAuthResponse(user, issued.session());
        return new Authenticated(
                auth,
                issued.rawRefreshToken(),
                Duration.between(issued.session().getIssuedAt(), issued.session().getExpiresAt()));
    }

    private static BusinessException locked(Instant lockedUntil) {
        return new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Too many failed attempts; try again later")
                .withProperty("lockedUntil", lockedUntil);
    }
}
