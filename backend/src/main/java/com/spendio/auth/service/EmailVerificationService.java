package com.spendio.auth.service;

import com.spendio.auth.domain.TokenPurpose;
import com.spendio.auth.domain.VerificationToken;
import com.spendio.auth.dto.response.MessageResponse;
import com.spendio.auth.repository.VerificationTokenRepository;
import com.spendio.auth.security.TokenCodec;
import com.spendio.mail.MailService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import com.spendio.users.service.ProfileService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification (ARCHITECTURE.md D.2, E.2): consume a 24-hour hashed token issued
 * at registration, or re-issue one (throttled per account). The caller must be the
 * account the token belongs to - both endpoints sit behind authentication (E.1).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    static final Duration TOKEN_TTL = Duration.ofHours(24);
    static final Duration RESEND_WINDOW = Duration.ofHours(24);
    static final int MAX_RESENDS_PER_DAY = 5;

    private final VerificationTokenRepository verificationTokenRepository;
    private final UserAccountService userAccountService;
    private final ProfileService profileService;
    private final MailService mailService;

    public EmailVerificationService(
            VerificationTokenRepository verificationTokenRepository,
            UserAccountService userAccountService,
            ProfileService profileService,
            MailService mailService) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.userAccountService = userAccountService;
        this.profileService = profileService;
        this.mailService = mailService;
    }

    @Transactional
    public MessageResponse verify(Long userId, String rawToken) {
        Optional<VerificationToken> maybe =
                verificationTokenRepository.findByTokenHash(TokenCodec.sha256Hex(rawToken));
        Instant now = Instant.now();
        if (maybe.isEmpty()
                || !maybe.get().isUsable(now)
                || !maybe.get().getUserId().equals(userId)
                || maybe.get().getPurpose() != TokenPurpose.EMAIL_VERIFY) {
            throw new BusinessException(
                    ErrorCode.INVALID_VERIFICATION_TOKEN, "Verification link is invalid or has expired");
        }
        VerificationToken token = maybe.get();
        token.consume(now);
        verificationTokenRepository.save(token);
        userAccountService.markEmailVerified(userId);
        log.info("security event=EMAIL_VERIFIED user_id={}", userId);
        return MessageResponse.of("Email address verified");
    }

    /** Re-sends when the account is still unverified and the daily budget allows it. */
    @Transactional
    public MessageResponse resend(Long userId) {
        UserResponse user = profileService.get(userId);
        if (user.emailVerified()) {
            return MessageResponse.of("If the address is unverified, a new link has been sent");
        }
        long recent = verificationTokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                userId, TokenPurpose.EMAIL_VERIFY, Instant.now().minus(RESEND_WINDOW));
        if (recent >= MAX_RESENDS_PER_DAY) {
            log.info("security event=VERIFICATION_RESEND_SUPPRESSED user_id={}", userId);
            return MessageResponse.of("If the address is unverified, a new link has been sent");
        }
        String raw = TokenCodec.generate();
        verificationTokenRepository.save(VerificationToken.issue(
                userId,
                TokenPurpose.EMAIL_VERIFY,
                TokenCodec.sha256Hex(raw),
                Instant.now().plus(TOKEN_TTL),
                null));
        mailService.sendEmailVerification(user.email(), raw);
        log.info("security event=VERIFICATION_RESENT user_id={}", userId);
        return MessageResponse.of("If the address is unverified, a new link has been sent");
    }
}
