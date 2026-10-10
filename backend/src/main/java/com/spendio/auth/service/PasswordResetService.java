package com.spendio.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spendio.auth.domain.PasswordResetToken;
import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.dto.request.ForgotPasswordRequest;
import com.spendio.auth.dto.request.ResetPasswordRequest;
import com.spendio.auth.dto.response.MessageResponse;
import com.spendio.auth.repository.PasswordResetTokenRepository;
import com.spendio.auth.security.PasswordPolicy;
import com.spendio.auth.security.TokenCodec;
import com.spendio.mail.MailService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot / reset password (ARCHITECTURE.md D.2, K.1): the request always answers the
 * same 202, is rate limited per IP (shared filter) and per email (this service),
 * invalidates outstanding tokens and mails a 30-minute single-use hashed token.
 * Resetting revokes every session and mails the owner a security notice.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration EMAIL_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_REQUESTS_PER_EMAIL = 5;

    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final UserAccountService userAccountService;
    private final SessionService sessionService;
    private final MailService mailService;

    private final Cache<String, EmailWindow> emailWindows = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(EMAIL_WINDOW)
            .build();

    public PasswordResetService(
            PasswordResetTokenRepository resetTokenRepository,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            UserAccountService userAccountService,
            SessionService sessionService,
            MailService mailService) {
        this.resetTokenRepository = resetTokenRepository;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.userAccountService = userAccountService;
        this.sessionService = sessionService;
        this.mailService = mailService;
    }

    public static final String ACK_MESSAGE =
            "If an account exists for that address, a password reset link has been sent.";

    /** Always the same 202 acknowledgement - never reveals whether the account exists. */
    @Transactional
    public MessageResponse request(ForgotPasswordRequest request, String ipAddress) {
        Optional<LoginStateResponse> account = userAccountService.loginStateFor(request.email());
        if (account.isPresent() && allowEmail(account.get().user().email())) {
            Long userId = account.get().user().id();
            resetTokenRepository.invalidateAllForUser(userId, Instant.now());
            String raw = TokenCodec.generate();
            resetTokenRepository.save(PasswordResetToken.issue(
                    userId,
                    TokenCodec.sha256Hex(raw),
                    Instant.now().plus(TOKEN_TTL),
                    ipAddress));
            mailService.sendPasswordReset(account.get().user().email(), raw);
            log.info("security event=PASSWORD_RESET_REQUESTED user_id={}", userId);
        }
        return MessageResponse.of(ACK_MESSAGE);
    }

    @Transactional
    public void reset(ResetPasswordRequest request) {
        Optional<PasswordResetToken> maybe =
                resetTokenRepository.findByTokenHash(TokenCodec.sha256Hex(request.token()));
        Instant now = Instant.now();
        if (maybe.isEmpty() || !maybe.get().isUsable(now)) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN, "Reset link is invalid or has expired");
        }
        PasswordResetToken token = maybe.get();

        Optional<LoginStateResponse> account = userAccountService.accountState(token.getUserId());
        if (account.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN, "Reset link is invalid or has expired");
        }
        UserResponse user = account.get().user();

        passwordPolicy.validate(request.newPassword(), user.email());
        if (passwordEncoder.matches(request.newPassword(), account.get().passwordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Password does not meet the policy")
                    .withProperty("violations", List.of("must-differ-from-current-password"));
        }

        token.markUsed(now);
        resetTokenRepository.save(token);
        userAccountService.changePassword(user.id(), passwordEncoder.encode(request.newPassword()));
        int revoked = sessionService.revokeAllForUser(user.id(), RevocationReason.PASSWORD_CHANGE);
        mailService.sendPasswordChanged(user.email());
        log.info("security event=PASSWORD_RESET user_id={} sessions_revoked={}", user.id(), revoked);
    }

    /** Per-email throttle on top of the shared per-IP filter (D.2). */
    private boolean allowEmail(String email) {
        EmailWindow window = emailWindows.get(email.toLowerCase(java.util.Locale.ROOT), key -> new EmailWindow());
        synchronized (window) {
            if (++window.count > MAX_REQUESTS_PER_EMAIL) {
                log.info("security event=PASSWORD_RESET_SUPPRESSED email={}", email);
                return false;
            }
            return true;
        }
    }

    private static final class EmailWindow {
        private int count;
    }
}
