package com.spendio.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.auth.domain.PasswordResetToken;
import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.dto.request.ForgotPasswordRequest;
import com.spendio.auth.dto.request.ResetPasswordRequest;
import com.spendio.auth.dto.response.MessageResponse;
import com.spendio.auth.repository.PasswordResetTokenRepository;
import com.spendio.auth.security.PasswordPolicy;
import com.spendio.mail.MailService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Forgot/reset password (ARCHITECTURE.md D.2, K.1). */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String HASH = "{bcrypt}$2a$12$abcdefghijklmnopqrstuv";

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private SessionService sessionService;

    @Mock
    private MailService mailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void forgotAlwaysReturnsTheSameAcknowledgementEvenForUnknownEmails() {
        when(userAccountService.loginStateFor("ghost@spendio.test")).thenReturn(Optional.empty());

        MessageResponse response = passwordResetService.request(
                new ForgotPasswordRequest("ghost@spendio.test"), "1.2.3.4");

        assertThat(response.message()).isEqualTo(PasswordResetService.ACK_MESSAGE);
        verify(resetTokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void forgotForAKnownEmailCreatesATokenAndMailsTheLink() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(enabledState()));

        MessageResponse response = passwordResetService.request(
                new ForgotPasswordRequest("ana@spendio.test"), "1.2.3.4");

        assertThat(response.message()).isEqualTo(PasswordResetService.ACK_MESSAGE);
        verify(resetTokenRepository).invalidateAllForUser(eq(7L), any());
        ArgumentCaptor<PasswordResetToken> token = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(token.capture());
        assertThat(token.getValue().getExpiresAt()).isAfter(Instant.now());
        verify(mailService).sendPasswordReset(eq("ana@spendio.test"), anyString());
    }

    @Test
    void forgotIsThrottledPerEmailButStillAcks() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(enabledState()));

        for (int i = 0; i < 7; i++) {
            MessageResponse response = passwordResetService.request(
                    new ForgotPasswordRequest("ana@spendio.test"), "1.2.3.4");
            assertThat(response.message()).isEqualTo(PasswordResetService.ACK_MESSAGE);
        }

        verify(resetTokenRepository, times(5)).save(any()); // MAX_REQUESTS_PER_EMAIL
    }

    @Test
    void resetRejectsAnUnknownToken() {
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.reset(new ResetPasswordRequest("nope", "Right123456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_RESET_TOKEN));
    }

    @Test
    void resetRejectsAnExpiredToken() {
        PasswordResetToken expired =
                PasswordResetToken.issue(7L, "hash", Instant.now().minus(java.time.Duration.ofMinutes(1)), "1.2.3.4");
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.reset(new ResetPasswordRequest("late", "Right123456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_RESET_TOKEN));
    }

    @Test
    void resetRejectsAConsumedToken() {
        PasswordResetToken used = PasswordResetToken.issue(7L, "hash", Instant.now().plus(java.time.Duration.ofMinutes(10)), "1.2.3.4");
        used.markUsed(Instant.now());
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> passwordResetService.reset(new ResetPasswordRequest("once", "Right123456")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_RESET_TOKEN));
    }

    @Test
    void resetThatReusesTheCurrentPasswordIsRejected() {
        PasswordResetToken token =
                PasswordResetToken.issue(7L, "hash", Instant.now().plus(java.time.Duration.ofMinutes(10)), "1.2.3.4");
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userAccountService.accountState(7L)).thenReturn(Optional.of(enabledState()));
        when(passwordEncoder.matches("Same12345678", HASH)).thenReturn(true);

        assertThatThrownBy(() -> passwordResetService.reset(new ResetPasswordRequest("tok", "Same12345678")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION));
    }

    @Test
    void successfulResetConsumesTheTokenReplacesThePasswordAndRevokesEverySession() {
        PasswordResetToken token =
                PasswordResetToken.issue(7L, "hash", Instant.now().plus(java.time.Duration.ofMinutes(10)), "1.2.3.4");
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(userAccountService.accountState(7L)).thenReturn(Optional.of(enabledState()));
        when(passwordEncoder.matches("New12345678", HASH)).thenReturn(false);
        when(passwordEncoder.encode("New12345678")).thenReturn("{bcrypt}$2a$12$newhash");

        passwordResetService.reset(new ResetPasswordRequest("tok", "New12345678"));

        assertThat(token.getUsedAt()).isNotNull();
        verify(userAccountService).changePassword(7L, "{bcrypt}$2a$12$newhash");
        verify(sessionService).revokeAllForUser(7L, RevocationReason.PASSWORD_CHANGE);
        verify(mailService).sendPasswordChanged("ana@spendio.test");
    }

    private static LoginStateResponse enabledState() {
        UserResponse user = new UserResponse(
                7L, "ana@spendio.test", "Ana", "Lopes", "Ana Lopes", "UTC", "en-GB", "USD", true, Instant.now());
        return new LoginStateResponse(user, HASH, true, null, 0, false);
    }
}
