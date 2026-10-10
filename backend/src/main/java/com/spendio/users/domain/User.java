package com.spendio.users.domain;

import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The account row: authentication material plus the profile shown across the app
 * (ARCHITECTURE.md C.3 #1, D.3).
 *
 * <p>Soft-deleted accounts keep their email rewritten (see {@link #anonymiseForDeletion()})
 * so the address can be registered again while audit history stays intact.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User extends SoftDeletableEntity {

    @Column(name = "email", length = 320, nullable = false)
    private String email;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @Column(name = "locale", length = 16, nullable = false)
    private String locale;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "default_currency_code", length = 3, nullable = false)
    private String defaultCurrencyCode;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_email_verified", nullable = false)
    private boolean emailVerified;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    protected User() {
    }

    private User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.timezone = "UTC";
        this.locale = "en-GB";
        this.defaultCurrencyCode = "USD";
        this.active = true;
        this.emailVerified = false;
        this.mustChangePassword = false;
        this.failedLoginAttempts = 0;
    }

    /** Normalises the address, derives the display name and applies the profile defaults. */
    public static User create(String email, String passwordHash, String timezone, String locale,
            String defaultCurrencyCode) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(passwordHash, "password hash must not be null");
        User user = new User(normaliseEmail(email), passwordHash);
        user.timezone = timezone;
        user.locale = locale;
        user.defaultCurrencyCode = defaultCurrencyCode;
        user.displayName = deriveDisplayName(user.email, null, null);
        return user;
    }

    public void updateProfile(String firstName, String lastName, String timezone, String locale) {
        this.firstName = emptyToNull(firstName);
        this.lastName = emptyToNull(lastName);
        this.timezone = timezone;
        this.locale = locale;
        this.displayName = deriveDisplayName(this.email, this.firstName, this.lastName);
    }

    public void changeDefaultCurrency(String currencyCode) {
        this.defaultCurrencyCode = currencyCode;
    }

    public void recordSuccessfulLogin(Instant at) {
        this.lastLoginAt = at;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void recordFailedLogin(int maxAttempts, Instant lockUntil) {
        this.failedLoginAttempts = this.failedLoginAttempts + 1;
        this.lockedUntil = this.failedLoginAttempts >= maxAttempts ? lockUntil : null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void deactivate() {
        this.active = false;
    }

    /** Consumes an email-verification token (Phase 2, E.2 verify-email). */
    public void markEmailVerified() {
        this.emailVerified = true;
    }

    /** Sets a new password hash and clears the forced-change flag (D.2 reset/change). */
    public void changePassword(String newPasswordHash) {
        setPasswordHash(newPasswordHash);
        this.mustChangePassword = false;
    }

    /** Rewrites the address before soft deletion so it can be registered again (C.3 #1). */
    public void anonymiseForDeletion() {
        this.email = "deleted+" + getId() + "@spendio.invalid";
        this.displayName = "Deleted user";
        this.firstName = null;
        this.lastName = null;
        this.active = false;
        this.softDelete();
    }

    public static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String deriveDisplayName(String email, String firstName, String lastName) {
        String composed = ((firstName == null ? "" : firstName.trim()) + " "
                + (lastName == null ? "" : lastName.trim())).trim();
        if (!composed.isEmpty()) {
            return composed.length() > 160 ? composed.substring(0, 160) : composed;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = Instant.now();
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public String getDefaultCurrencyCode() {
        return defaultCurrencyCode;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
}
