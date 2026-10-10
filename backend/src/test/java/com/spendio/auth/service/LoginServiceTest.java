package com.spendio.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.auth.dto.request.LoginRequest;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.auth.domain.UserSession;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Login behaviour (ARCHITECTURE.md D.2, I.4): enumeration-safe generic failure,
 * lockout before and after the threshold, disabled accounts, session creation.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessionService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private LoginService loginService;

    private static final String HASH = "{bcrypt}$2a$12$abcdefghijklmnopqrstuv";

    @Test
    void unknownEmailFailsWithTheSameGenericErrorAsWrongPassword() {
        when(userAccountService.loginStateFor("ghost@spendio.test")).thenReturn(Optional.empty());

        // The dummy-hash match runs against an unstubbed mock (false) - same code path,
        // same generic error, no user record ever loaded.
        assertThatThrownBy(() -> loginService.login(request("ghost@spendio.test", "Whatever123"), "1.2.3.4", "curl"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(userAccountService, never()).recordLoginFailure(any());
    }

    @Test
    void wrongPasswordRecordsAFailureAndReturns401() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(state(false)));
        when(passwordEncoder.matches("Nope123456", HASH)).thenReturn(false);
        when(userAccountService.recordLoginFailure(7L)).thenReturn(state(false));

        assertThatThrownBy(() -> loginService.login(request("ana@spendio.test", "Nope123456"), "1.2.3.4", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS));

        verify(userAccountService).recordLoginFailure(7L);
        verify(sessionService, never()).createSession(any(), eq(false), any(), any());
    }

    @Test
    void theFailingAttemptThatCrossesTheThresholdAnswers423() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(state(false)));
        when(passwordEncoder.matches("Nope123456", HASH)).thenReturn(false);
        LoginStateResponse locked = new LoginStateResponse(
                state(false).user(), HASH, true, Instant.now().plusSeconds(900), 5, false);
        when(userAccountService.recordLoginFailure(7L)).thenReturn(locked);

        assertThatThrownBy(() -> loginService.login(request("ana@spendio.test", "Nope123456"), "1.2.3.4", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
                    assertThat(((BusinessException) ex).getProperties()).containsKey("lockedUntil");
                });
    }

    @Test
    void alreadyLockedAccountIsRejectedBeforeThePasswordIsEvenChecked() {
        Instant until = Instant.now().plusSeconds(900);
        when(userAccountService.loginStateFor("ana@spendio.test"))
                .thenReturn(Optional.of(new LoginStateResponse(state(false).user(), HASH, true, until, 5, false)));

        assertThatThrownBy(() -> loginService.login(request("ana@spendio.test", "Right123456"), "1.2.3.4", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_LOCKED));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void disabledAccountAnswers403() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(state(true)));

        assertThatThrownBy(() -> loginService.login(request("ana@spendio.test", "Right123456"), "1.2.3.4", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_DISABLED));
    }

    @Test
    void successResetsCountersAndCreatesARememberedSession() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(state(false)));
        when(passwordEncoder.matches("Right123456", HASH)).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(HASH)).thenReturn(false);
        when(userAccountService.summary(7L)).thenReturn(state(false).user());
        when(sessionService.createSession(7L, true, "1.2.3.4", "curl"))
                .thenReturn(issued());
        when(tokenService.issueAuthResponse(any(), any())).thenReturn(AuthResponse.of("jwt", 900, state(false).user()));

        LoginRequest request = new LoginRequest("ana@spendio.test", "Right123456", true);
        Authenticated result = loginService.login(request, "1.2.3.4", "curl");

        verify(userAccountService).recordLoginSuccess(7L);
        assertThat(result.auth().accessToken()).isEqualTo("jwt");
        assertThat(result.rawRefreshToken()).isNotBlank();
        assertThat(result.refreshCookieTtl()).isPositive();
    }

    @Test
    void successUpgradesTheHashWhenTheEncoderSaysSo() {
        when(userAccountService.loginStateFor("ana@spendio.test")).thenReturn(Optional.of(state(false)));
        when(passwordEncoder.matches("Right123456", HASH)).thenReturn(true);
        when(passwordEncoder.upgradeEncoding(HASH)).thenReturn(true);
        when(passwordEncoder.encode("Right123456")).thenReturn("{bcrypt}$2a$12$newnewnewnewnewnewnew");
        when(userAccountService.summary(7L)).thenReturn(state(false).user());
        when(sessionService.createSession(eq(7L), eq(false), any(), any())).thenReturn(issued());
        when(tokenService.issueAuthResponse(any(), any())).thenReturn(AuthResponse.of("jwt", 900, state(false).user()));

        loginService.login(request("ana@spendio.test", "Right123456"), "1.2.3.4", null);

        verify(userAccountService).changePassword(7L, "{bcrypt}$2a$12$newnewnewnewnewnewnew");
        verify(userAccountService).recordLoginSuccess(7L);
    }

    private static LoginRequest request(String email, String password) {
        return new LoginRequest(email, password, false);
    }

    private static LoginStateResponse state(boolean disabled) {
        UserResponse user = new UserResponse(
                7L, "ana@spendio.test", "Ana", "Lopes", "Ana Lopes", "UTC", "en-GB", "USD", true, Instant.now());
        return new LoginStateResponse(user, HASH, !disabled, null, 0, false);
    }

    private static SessionService.IssuedSession issued() {
        UserSession session = UserSession.issue(
                7L,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "hash",
                Instant.now(),
                Duration.ofDays(30),
                "1.2.3.4",
                "curl",
                "curl on Device");
        return new SessionService.IssuedSession(session, "raw-refresh-token");
    }
}
