package com.spendio.auth.service;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.dto.request.ChangePasswordRequest;
import com.spendio.auth.security.PasswordPolicy;
import com.spendio.mail.MailService;
import com.spendio.sharedkernel.audit.AuditEntry;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.PasswordChangedEvent;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;;

/**
 * POST /auth/change-password (ARCHITECTURE.md D.2, K.1): current-password check,
 * policy check, hash replacement, every session revoked except the current one,
 * security mail to the owner.
 */
@Service
public class ChangePasswordService {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordService.class);

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final MailService mailService;
    private final com.spendio.sharedkernel.audit.AuditRecorder auditRecorder;
    private final ApplicationEventPublisher events;

    public ChangePasswordService(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SessionService sessionService,
            MailService mailService,
            com.spendio.sharedkernel.audit.AuditRecorder auditRecorder,
            ApplicationEventPublisher events) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.mailService = mailService;
        this.auditRecorder = auditRecorder;
        this.events = events;
    }

    @Transactional
    public void change(Long userId, String currentSessionUuid, ChangePasswordRequest request) {
        Optional<LoginStateResponse> maybe = userAccountService.accountState(userId);
        if (maybe.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found");
        }
        LoginStateResponse state = maybe.get();
        UserResponse user = state.user();

        if (!passwordEncoder.matches(request.currentPassword(), state.passwordHash())) {
            log.info("security event=PASSWORD_CHANGE_FAILED user_id={} reason=current_password_mismatch", userId);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }

        passwordPolicy.validate(request.newPassword(), user.email());
        if (passwordEncoder.matches(request.newPassword(), state.passwordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Password does not meet the policy")
                    .withProperty("violations", List.of("must-differ-from-current-password"));
        }

        userAccountService.changePassword(userId, passwordEncoder.encode(request.newPassword()));
        int revoked = 0;
        if (currentSessionUuid != null) {
            revoked = sessionService.revokeAllExcept(userId, currentSessionUuid, RevocationReason.PASSWORD_CHANGE);
        } else {
            revoked = sessionService.revokeAllForUser(userId, RevocationReason.PASSWORD_CHANGE);
        }
        mailService.sendPasswordChanged(user.email());
        auditRecorder.record(com.spendio.sharedkernel.audit.AuditEntry.user(
                userId, "PASSWORD_CHANGED", "USER", userId));
        Instant changedAt = Instant.now();
        events.publishEvent(new PasswordChangedEvent(userId, changedAt));
        log.info(
                "security event=PASSWORD_CHANGED user_id={} other_sessions_revoked={} at={}",
                userId,
                revoked,
                changedAt);
    }
}
