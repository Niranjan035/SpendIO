package com.spendio.auth.service;

import com.spendio.auth.dto.request.RegisterRequest;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.auth.repository.VerificationTokenRepository;
import com.spendio.auth.domain.TokenPurpose;
import com.spendio.auth.domain.VerificationToken;
import com.spendio.auth.security.PasswordPolicy;
import com.spendio.auth.security.TokenCodec;
import com.spendio.mail.MailService;
import com.spendio.sharedkernel.events.NewUserRegisteredEvent;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import com.spendio.users.service.UserAccountService;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /auth/register (ARCHITECTURE.md D.2, K.1): policy check, BCrypt hash,
 * account + preference creation via the users module, first session, verification
 * token, welcome mail, registration event.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    static final Duration VERIFICATION_TTL = Duration.ofHours(24);

    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final ProfileService profileService;
    private final UserAccountService userAccountService;
    private final SessionService sessionService;
    private final TokenService tokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final MailService mailService;
    private final ApplicationEventPublisher eventPublisher;

    public RegistrationService(
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            ProfileService profileService,
            UserAccountService userAccountService,
            SessionService sessionService,
            TokenService tokenService,
            VerificationTokenRepository verificationTokenRepository,
            MailService mailService,
            ApplicationEventPublisher eventPublisher) {
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.profileService = profileService;
        this.userAccountService = userAccountService;
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.verificationTokenRepository = verificationTokenRepository;
        this.mailService = mailService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Authenticated register(RegisterRequest request, String ipAddress, String userAgent) {
        passwordPolicy.validate(request.password(), request.email());

        CreateUserCommand command = new CreateUserCommand(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                defaultIfBlank(request.timezone(), "UTC"),
                defaultIfBlank(request.locale(), "en-GB"),
                defaultIfBlank(request.baseCurrencyCode(), "USD"));
        UserResponse user = profileService.create(command);

        SessionService.IssuedSession issued =
                sessionService.createSession(user.id(), false, ipAddress, userAgent);
        issueEmailVerification(user);
        mailService.sendWelcome(user.email(), user.displayName());
        eventPublisher.publishEvent(new NewUserRegisteredEvent(
                user.id(),
                user.email(),
                user.displayName(),
                user.locale(),
                user.timezone(),
                user.baseCurrencyCode(),
                Instant.now()));
        log.info("security event=REGISTER user_id={}", user.id());

        AuthResponse auth = tokenService.issueAuthResponse(user, issued.session());
        return new Authenticated(
                auth,
                issued.rawRefreshToken(),
                Duration.between(issued.session().getIssuedAt(), issued.session().getExpiresAt()));
    }

    private void issueEmailVerification(UserResponse user) {
        String raw = TokenCodec.generate();
        verificationTokenRepository.save(VerificationToken.issue(
                user.id(),
                TokenPurpose.EMAIL_VERIFY,
                TokenCodec.sha256Hex(raw),
                Instant.now().plus(VERIFICATION_TTL),
                null));
        mailService.sendEmailVerification(user.email(), raw);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
