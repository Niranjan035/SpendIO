package com.spendio.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.domain.UserSession;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.auth.repository.UserSessionRepository;
import com.spendio.auth.security.TokenCodec;
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

/**
 * Refresh rotation (ARCHITECTURE.md D.2, I.4): rotation chain, reuse detection
 * revoking the whole family durably, expired and disabled-account rejection.
 */
@ExtendWith(MockitoExtension.class)
class TokenRotationServiceTest {

    private static final String RAW = "opaque-refresh-token";

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserAccountService userAccountService;

    @InjectMocks
    private TokenRotationService tokenRotationService;

    @Test
    void missingTokenIsRejected() {
        assertThatThrownBy(() -> tokenRotationService.rotate(null, "1.2.3.4", "curl"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void unknownTokenIsRejected() {
        when(sessionRepository.findByRefreshTokenHash(TokenCodec.sha256Hex(RAW)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenRotationService.rotate(RAW, "1.2.3.4", "curl"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void presentingARevokedTokenRevokesTheWholeFamilyAndFails() {
        UserSession revoked = session(Raw.STATUS_REVOKED);
        when(sessionRepository.findByRefreshTokenHash(TokenCodec.sha256Hex(RAW)))
                .thenReturn(Optional.of(revoked));
        when(sessionService.revokeFamily(eq(revoked.getFamilyId()), eq(RevocationReason.REUSE_DETECTED)))
                .thenReturn(3);

        assertThatThrownBy(() -> tokenRotationService.rotate(RAW, "1.2.3.4", "curl"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.TOKEN_REUSE_DETECTED));

        verify(sessionService).revokeFamily(revoked.getFamilyId(), RevocationReason.REUSE_DETECTED);
    }

    @Test
    void expiredSessionIsRevokedAndRejected() {
        UserSession expired = session(Raw.STATUS_EXPIRED);
        when(sessionRepository.findByRefreshTokenHash(TokenCodec.sha256Hex(RAW)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> tokenRotationService.rotate(RAW, "1.2.3.4", "curl"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));

        verify(sessionService).revoke(expired, RevocationReason.EXPIRED);
    }

    @Test
    void disabledAccountRevokesTheFamilyAndFails() {
        UserSession active = session(Raw.STATUS_ACTIVE);
        when(sessionRepository.findByRefreshTokenHash(TokenCodec.sha256Hex(RAW)))
                .thenReturn(Optional.of(active));
        when(userAccountService.accountState(7L)).thenReturn(Optional.of(disabledState()));

        assertThatThrownBy(() -> tokenRotationService.rotate(RAW, "1.2.3.4", "curl"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_DISABLED));

        verify(sessionService).revokeFamily(active.getFamilyId(), RevocationReason.ADMIN);
    }

    @Test
    void happyPathRotatesWithinTheFamilyAndIssuesNewTokens() {
        UserSession active = session(Raw.STATUS_ACTIVE);
        when(sessionRepository.findByRefreshTokenHash(TokenCodec.sha256Hex(RAW)))
                .thenReturn(Optional.of(active));
        when(userAccountService.accountState(7L)).thenReturn(Optional.of(enabledState()));
        when(sessionService.rotateInto(eq(active), eq("1.2.3.4"), eq("curl"))).thenReturn(rotated());
        when(userAccountService.summary(7L)).thenReturn(enabledState().user());
        when(tokenService.issueAuthResponse(any(), any())).thenReturn(AuthResponse.of("jwt", 900, enabledState().user()));

        Authenticated result = tokenRotationService.rotate(RAW, "1.2.3.4", "curl");

        verify(sessionService, never()).revokeFamily(anyString(), any());
        verify(sessionRepository).save(active);
        assertThat(result.auth().accessToken()).isEqualTo("jwt");
        assertThat(result.rawRefreshToken()).isEqualTo("raw-next-token");
        assertThat(active.getRevocationReason()).isEqualTo(RevocationReason.ROTATED);
    }

    private static UserSession session(Raw status) {
        UserSession session = UserSession.issue(
                7L,
                UUID.randomUUID().toString(),
                "family-1",
                TokenCodec.sha256Hex(RAW),
                status == Raw.STATUS_EXPIRED ? Instant.now().minus(java.time.Duration.ofDays(40)) : Instant.now(),
                Duration.ofDays(30),
                "1.2.3.4",
                "curl",
                "curl on Device");
        if (status == Raw.STATUS_REVOKED) {
            session.revoke(RevocationReason.LOGOUT, Instant.now());
        }
        return session;
    }

    private enum Raw {
        STATUS_ACTIVE,
        STATUS_REVOKED,
        STATUS_EXPIRED
    }

    private static SessionService.IssuedSession rotated() {
        UserSession next = UserSession.issue(
                7L,
                UUID.randomUUID().toString(),
                "family-1",
                "next-hash",
                Instant.now(),
                Duration.ofDays(30),
                "1.2.3.4",
                "curl",
                "curl on Device");
        return new SessionService.IssuedSession(next, "raw-next-token");
    }

    private static LoginStateResponse enabledState() {
        UserResponse user = new UserResponse(
                7L, "ana@spendio.test", "Ana", "Lopes", "Ana Lopes", "UTC", "en-GB", "USD", true, Instant.now());
        return new LoginStateResponse(user, "{bcrypt}x", true, null, 0, false);
    }

    private static LoginStateResponse disabledState() {
        UserResponse user = new UserResponse(
                7L, "ana@spendio.test", "Ana", "Lopes", "Ana Lopes", "UTC", "en-GB", "USD", true, Instant.now());
        return new LoginStateResponse(user, "{bcrypt}x", false, null, 0, false);
    }
}
