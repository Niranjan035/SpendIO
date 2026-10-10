package com.spendio.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.auth.domain.TokenPurpose;
import com.spendio.auth.domain.VerificationToken;
import com.spendio.auth.dto.request.RegisterRequest;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.auth.repository.VerificationTokenRepository;
import com.spendio.auth.security.PasswordPolicy;
import com.spendio.mail.MailService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.events.NewUserRegisteredEvent;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import com.spendio.users.service.UserAccountService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registration (ARCHITECTURE.md D.2, K.1): policy first, then account creation via
 * the users module, first session, verification token, welcome mail, event.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProfileService profileService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private SessionService sessionService;

    @Mock
    private TokenService tokenService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private MailService mailService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void weakPasswordIsRejectedBeforeAnythingHappens() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "weak"))
                .when(passwordPolicy)
                .validate("short", "ana@spendio.test");

        assertThatThrownBy(() -> registrationService.register(request("ana@spendio.test", "short"), "1.2.3.4", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PASSWORD_POLICY_VIOLATION));

        verify(profileService, never()).create(any());
        verify(verificationTokenRepository, never()).save(any());
    }

    @Test
    void takenEmailPropagatesAsConflict() {
        when(profileService.create(any()))
                .thenThrow(new BusinessException(ErrorCode.USER_EMAIL_TAKEN, "Email already registered"));

        assertThatThrownBy(() -> registrationService.register(request("ana@spendio.test", "Right123456"), null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.USER_EMAIL_TAKEN));

        verify(sessionService, never()).createSession(any(), eq(false), any(), any());
    }

    @Test
    void happyPathCreatesAccountSessionVerificationAndMails() {
        when(passwordEncoder.encode("Right123456")).thenReturn("{bcrypt}$2a$12$hash");
        when(profileService.create(any())).thenReturn(user());
        when(sessionService.createSession(eq(9L), eq(false), eq("1.2.3.4"), anyString()))
                .thenReturn(issued());
        when(tokenService.issueAuthResponse(any(), any())).thenReturn(AuthResponse.of("jwt", 900, user()));

        RegisterRequest request = new RegisterRequest(
                "ana@spendio.test", "Right123456", "Ana", "Lopes", null, null, null);
        Authenticated result = registrationService.register(request, "1.2.3.4", "curl");

        ArgumentCaptor<CreateUserCommand> command = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(profileService).create(command.capture());
        assertThat(command.getValue().passwordHash()).isEqualTo("{bcrypt}$2a$12$hash");
        assertThat(command.getValue().timezone()).isEqualTo("UTC");
        assertThat(command.getValue().locale()).isEqualTo("en-GB");
        assertThat(command.getValue().baseCurrencyCode()).isEqualTo("USD");

        ArgumentCaptor<VerificationToken> token = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(token.capture());
        assertThat(token.getValue().getPurpose()).isEqualTo(TokenPurpose.EMAIL_VERIFY);
        assertThat(token.getValue().getExpiresAt()).isAfter(Instant.now());

        verify(mailService).sendWelcome(eq("ana@spendio.test"), eq("Ana Lopes"));
        verify(mailService).sendEmailVerification(eq("ana@spendio.test"), anyString());
        verify(eventPublisher).publishEvent(any(NewUserRegisteredEvent.class));

        assertThat(result.auth().user().email()).isEqualTo("ana@spendio.test");
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(result.refreshCookieTtl()).isPositive();
    }

    private static RegisterRequest request(String email, String password) {
        return new RegisterRequest(email, password, null, null, null, null, null);
    }

    private static UserResponse user() {
        return new UserResponse(
                9L, "ana@spendio.test", "Ana", "Lopes", "Ana Lopes", "UTC", "en-GB", "USD", false, Instant.now());
    }

    private static SessionService.IssuedSession issued() {
        var session = com.spendio.auth.domain.UserSession.issue(
                9L,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "hash",
                Instant.now(),
                java.time.Duration.ofDays(30),
                "1.2.3.4",
                "curl",
                "curl on Device");
        return new SessionService.IssuedSession(session, "raw-refresh-token");
    }
}
