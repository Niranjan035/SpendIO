package com.spendio.auth.security;

import com.spendio.config.SecurityProperties;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Length-first password policy (ARCHITECTURE.md D.2, I.4): min/max length, one upper,
 * one lower, one digit, not the email local part, optionally not in the breached list.
 *
 * <p>Runs in the services (not Bean Validation) so violations surface as
 * {@code PASSWORD_POLICY_VIOLATION} 422 with the exact failing rules, per the
 * exception contract in D.2.
 */
@Service
public class PasswordPolicy {

    private final SecurityProperties.Password properties;
    private final BreachedPasswordChecker breachedPasswordChecker;

    public PasswordPolicy(SecurityProperties properties, BreachedPasswordChecker breachedPasswordChecker) {
        this.properties = properties.password();
        this.breachedPasswordChecker = breachedPasswordChecker;
    }

    /** Validates and throws 422 with every violated rule at once. */
    public void validate(String rawPassword, String email) {
        List<String> violations = check(rawPassword, email);
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "Password does not meet the policy")
                    .withProperty("violations", violations);
        }
    }

    /** Non-throwing form used to surface rules up front. */
    public List<String> check(String rawPassword, String email) {
        List<String> violations = new ArrayList<>();
        if (rawPassword == null || rawPassword.length() < properties.minLength()) {
            violations.add("at-least-" + properties.minLength() + "-characters");
            return violations;
        }
        if (rawPassword.length() > properties.maxLength()) {
            violations.add("at-most-" + properties.maxLength() + "-characters");
            return violations;
        }
        if (!rawPassword.matches(".*[a-z].*")) {
            violations.add("a-lowercase-letter");
        }
        if (!rawPassword.matches(".*[A-Z].*")) {
            violations.add("an-uppercase-letter");
        }
        if (!rawPassword.matches(".*\\d.*")) {
            violations.add("a-digit");
        }
        if (isEmailLocalPart(rawPassword, email)) {
            violations.add("not-your-email-address");
        }
        if (violations.isEmpty() && breachedPasswordChecker.isBreached(rawPassword)) {
            violations.add("not-a-known-breached-password");
        }
        return violations;
    }

    private static boolean isEmailLocalPart(String password, String email) {
        if (email == null || !email.contains("@")) {
            return false;
        }
        String localPart = email.substring(0, email.indexOf('@')).trim().toLowerCase(Locale.ROOT);
        return !localPart.isEmpty() && password.trim().toLowerCase(Locale.ROOT).equals(localPart);
    }
}
