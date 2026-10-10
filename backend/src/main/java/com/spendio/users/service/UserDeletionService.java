package com.spendio.users.service;

import com.spendio.sharedkernel.audit.AuditEntry;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.UserAccountDeletedEvent;
import com.spendio.users.domain.User;
import com.spendio.users.dto.request.DeleteAccountRequest;
import com.spendio.users.repository.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account deletion (ARCHITECTURE.md D.3, Phase 14): password confirmation plus a typed
 * acknowledgement, then a soft delete that anonymises the row so the email can be
 * reused while the ledger and audit trail survive. Side effects (session revocation,
 * upload purge, notification, audit anonymisation) are handled by kernel-event
 * listeners in the owning modules, so this service keeps the declared {@code users}
 * edges only (B.3). A hard purge of the row happens after the grace window.
 */
@Service
public class UserDeletionService {

    /** The exact phrase a caller must type to confirm a destructive delete. */
    public static final String CONFIRMATION_PHRASE = "DELETE";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final ApplicationEventPublisher events;

    public UserDeletionService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
        this.events = events;
    }

    @Transactional
    public void delete(Long userId, DeleteAccountRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));

        if (request.confirmationText() == null
                || !CONFIRMATION_PHRASE.equals(request.confirmationText().trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Type " + CONFIRMATION_PHRASE + " to confirm")
                    .withProperty("field", "confirmationText");
        }
        if (request.password() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Password confirmation failed");
        }

        String email = user.getEmail();
        auditRecorder.record(AuditEntry.user(userId, "ACCOUNT_DELETED", "USER", userId));
        user.anonymiseForDeletion();
        userRepository.save(user);
        events.publishEvent(new UserAccountDeletedEvent(userId, email, Instant.now()));
    }
}
