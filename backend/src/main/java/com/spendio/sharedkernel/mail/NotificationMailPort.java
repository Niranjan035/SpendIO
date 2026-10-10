package com.spendio.sharedkernel.mail;

/**
 * Outbound notification email (ARCHITECTURE.md D.13). The notifications module is only
 * allowed to depend on the users module (B.3), so it reaches the mail implementation
 * through this shared-kernel port - the same inversion {@link
 * com.spendio.sharedkernel.money.FxConversionPort} uses. The mail module implements it;
 * failures are logged by the implementation, never thrown.
 */
public interface NotificationMailPort {

    void sendNotification(String to, String subject, String body);
}