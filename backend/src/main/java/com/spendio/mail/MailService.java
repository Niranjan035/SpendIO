package com.spendio.mail;

import com.spendio.config.MailProperties;
import com.spendio.sharedkernel.mail.NotificationMailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Transactional outbound mail (ARCHITECTURE.md D.13, F.5).
 *
 * <p>Deliberately boring: plain text bodies rendered from a template map, one public
 * method per mail type the product sends today (welcome, verification, password
 * reset, password changed). Failures are logged, never thrown - missing a mail must
 * not roll back the business transaction (P1 risks).
 */
@Service
@EnableConfigurationProperties(MailProperties.class)
public class MailService implements NotificationMailPort {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public MailService(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendWelcome(String to, String displayName) {
        send(to, "Welcome to SpendIO", """
                Hi %s,

                Your SpendIO account is ready. Sign in and start tracking your
                money in under a minute.

                The SpendIO team
                """.formatted(displayName));
    }

    public void sendEmailVerification(String to, String token) {
        String link = properties.baseUrl() + "/auth/verify-email?token=" + token;
        send(to, "Verify your SpendIO email", """
                Hi,

                Confirm this address for SpendIO:

                %s

                The link expires in 24 hours. If you did not create a SpendIO
                account you can ignore this message.

                The SpendIO team
                """.formatted(link));
    }

    public void sendPasswordReset(String to, String token) {
        String link = properties.baseUrl() + "/auth/reset-password?token=" + token;
        send(to, "Reset your SpendIO password", """
                Hi,

                Choose a new password here (valid for 1 hour):

                %s

                If you did not request this, ignore this message - your password
                is unchanged.

                The SpendIO team
                """.formatted(link));
    }

    public void sendPasswordChanged(String to) {
        send(to, "Your SpendIO password was changed", """
                Hi,

                Your password was just changed. All other sessions have been
                signed out. If this was not you, reset your password immediately.

                The SpendIO team
                """);
    }

    /** Notification email channel (Phase 11): a plain, quoted notification body. */
    @Override
    public void sendNotification(String to, String subject, String body) {
        send(to, subject, body.strip().isBlank() ? subject : body.strip());
    }

    private void send(String to, String subject, String body) {
        if (!properties.enabled()) {
            log.info("mail disabled - to={} subject='{}'", to, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.from());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("mail sent - to={} subject='{}'", to, subject);
        } catch (RuntimeException ex) {
            log.warn("mail delivery failed - to={} subject='{}': {}", to, subject, ex.getMessage());
        }
    }
}
