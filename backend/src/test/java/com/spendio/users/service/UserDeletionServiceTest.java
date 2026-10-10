package com.spendio.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.sharedkernel.audit.AuditEntry;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.UserAccountDeletedEvent;
import com.spendio.users.domain.User;
import com.spendio.users.dto.request.DeleteAccountRequest;
import com.spendio.users.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Account deletion (ARCHITECTURE.md D.3, Phase 14): confirmed, audited and announced. */
@ExtendWith(MockitoExtension.class)
class UserDeletionServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private UserDeletionService service;

    private static User savedUser() {
        User user = User.create("ana@example.com", "{bcrypt}", "Europe/Lisbon", "en-GB", "USD");
        user.updateProfile("Ana", "Lopes", "Europe/Lisbon", "en-GB");
        return user;
    }

    @Test
    void anonymisesAuditsAndPublishesOnSuccess() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret123!", "{bcrypt}")).thenReturn(true);

        service.delete(7L, new DeleteAccountRequest("Secret123!", "DELETE"));

        assertThat(user.getEmail()).isEqualTo("deleted+null@spendio.invalid");
        assertThat(user.getDisplayName()).isEqualTo("Deleted user");
        assertThat(user.isDeleted()).isTrue();
        verify(userRepository).save(user);

        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("ACCOUNT_DELETED");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(UserAccountDeletedEvent.class);
        assertThat(((UserAccountDeletedEvent) event.getValue()).email()).isEqualTo("ana@example.com");
    }

    @Test
    void rejectsWrongPasswordWithoutTouchingTheAccount() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("nope", "{bcrypt}")).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L, new DeleteAccountRequest("nope", "DELETE")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void rejectsWrongConfirmationPhrase() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.delete(7L, new DeleteAccountRequest("Secret123!", "delete me")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    void rejectsUnknownUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, new DeleteAccountRequest("x", "DELETE")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
