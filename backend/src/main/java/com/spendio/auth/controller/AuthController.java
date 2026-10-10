package com.spendio.auth.controller;

import com.spendio.auth.dto.request.ChangePasswordRequest;
import com.spendio.auth.dto.request.ForgotPasswordRequest;
import com.spendio.auth.dto.request.LoginRequest;
import com.spendio.auth.dto.request.RegisterRequest;
import com.spendio.auth.dto.request.ResetPasswordRequest;
import com.spendio.auth.dto.request.VerifyEmailRequest;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.auth.dto.response.MessageResponse;
import com.spendio.auth.service.ChangePasswordService;
import com.spendio.auth.service.EmailVerificationService;
import com.spendio.auth.service.LogoutService;
import com.spendio.auth.service.LoginService;
import com.spendio.auth.service.PasswordResetService;
import com.spendio.auth.service.RegistrationService;
import com.spendio.auth.service.TokenRotationService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.security.AuthRateLimitFilter;
import com.spendio.sharedkernel.security.RefreshCookieService;
import com.spendio.sharedkernel.security.SecurityUtils;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auth surface (ARCHITECTURE.md E.2, D.2). Cookie-bearing routes are CSRF
 * protected by the security chain; everything else identifies callers with the
 * bearer access token. The refresh token only ever travels in the {@code spendio_rt}
 * cookie - never in a body.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final TokenRotationService tokenRotationService;
    private final LogoutService logoutService;
    private final PasswordResetService passwordResetService;
    private final ChangePasswordService changePasswordService;
    private final EmailVerificationService emailVerificationService;
    private final ProfileService profileService;
    private final RefreshCookieService refreshCookieService;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            TokenRotationService tokenRotationService,
            LogoutService logoutService,
            PasswordResetService passwordResetService,
            ChangePasswordService changePasswordService,
            EmailVerificationService emailVerificationService,
            ProfileService profileService,
            RefreshCookieService refreshCookieService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.tokenRotationService = tokenRotationService;
        this.logoutService = logoutService;
        this.passwordResetService = passwordResetService;
        this.changePasswordService = changePasswordService;
        this.emailVerificationService = emailVerificationService;
        this.profileService = profileService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest http, HttpServletResponse response) {
        Authenticated result = registrationService.register(request, clientIp(http), userAgent(http));
        refreshCookieService.writeRawToken(response, result.rawRefreshToken(), result.refreshCookieTtl());
        return ResponseEntity.status(HttpStatus.CREATED).body(result.auth());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        Authenticated result = loginService.login(request, clientIp(http), userAgent(http));
        refreshCookieService.writeRawToken(response, result.rawRefreshToken(), result.refreshCookieTtl());
        return ResponseEntity.ok(result.auth());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http, HttpServletResponse response) {
        Authenticated result =
                tokenRotationService.rotate(refreshCookieService.readRawToken(http), clientIp(http), userAgent(http));
        refreshCookieService.writeRawToken(response, result.rawRefreshToken(), result.refreshCookieTtl());
        return ResponseEntity.ok(result.auth());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http, HttpServletResponse response) {
        logoutService.logout(refreshCookieService.readRawToken(http));
        refreshCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(HttpServletResponse response) {
        logoutService.logoutAll(SecurityUtils.requireUserId());
        refreshCookieService.clear(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        return ResponseEntity.accepted()
                .body(passwordResetService.request(request, clientIp(http)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserPrincipal principal = requirePrincipal();
        String currentSession = principal.sessionId() == null ? null : principal.sessionId().toString();
        changePasswordService.change(principal.userId(), currentSession, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(emailVerificationService.verify(SecurityUtils.requireUserId(), request.token()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification() {
        return ResponseEntity.accepted()
                .body(emailVerificationService.resend(SecurityUtils.requireUserId()));
    }

    @GetMapping("/me")
    public UserResponse me() {
        return profileService.get(SecurityUtils.requireUserId());
    }

    private static UserPrincipal requirePrincipal() {
        return SecurityUtils.currentPrincipal()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_MISSING, "Authentication is required"));
    }

    private static String clientIp(HttpServletRequest http) {
        return AuthRateLimitFilter.clientIp(http);
    }

    private static String userAgent(HttpServletRequest http) {
        return http.getHeader("User-Agent");
    }
}
