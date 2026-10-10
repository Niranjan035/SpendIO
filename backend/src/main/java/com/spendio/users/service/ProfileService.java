package com.spendio.users.service;

import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.audit.AuditEntry;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.users.dto.request.ChangeBaseCurrencyRequest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.request.UpdateProfileRequest;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.domain.User;
import com.spendio.users.domain.UserPreference;
import com.spendio.users.repository.UserPreferenceRepository;
import com.spendio.users.repository.UserRepository;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile lifecycle for the authenticated account (ARCHITECTURE.md E.3, D.3).
 *
 * <p>Account creation is exposed here rather than through registration so the same
 * command is usable from the invite/seed paths and from tests.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final CurrencyService currencyService;
    private final AuditRecorder auditRecorder;

    public ProfileService(
            UserRepository userRepository,
            UserPreferenceRepository userPreferenceRepository,
            CurrencyService currencyService,
            AuditRecorder auditRecorder) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.currencyService = currencyService;
        this.auditRecorder = auditRecorder;
    }

    /** Creates the account plus its preference row; rolls back together (C.3 #1, #2). */
    @Transactional
    public UserResponse create(CreateUserCommand command) {
        String email = User.normaliseEmail(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.USER_EMAIL_TAKEN, "Email already registered")
                    .withProperty("email", email);
        }
        validateTimezone(command.timezone());
        validateLocale(command.locale());
        String currencyCode = command.baseCurrencyCode().toUpperCase(Locale.ROOT);
        currencyService.requireSupported(currencyCode);

        User user = User.create(email, command.passwordHash(), command.timezone(), command.locale(), currencyCode);
        user.updateProfile(command.firstName(), command.lastName(), command.timezone(), command.locale());
        user = userRepository.saveAndFlush(user);
        userPreferenceRepository.save(UserPreference.defaultsFor(user.getId()));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long userId) {
        return UserResponse.from(requireActive(userId));
    }

    /**
     * PATCH semantics: every field is optional and a {@code null} means "leave as it is".
     */
    @Transactional
    public UserResponse update(Long userId, UpdateProfileRequest request) {
        User user = requireActive(userId);
        String timezone = request.timezone() != null ? request.timezone() : user.getTimezone();
        String locale = request.locale() != null ? request.locale() : user.getLocale();
        validateTimezone(timezone);
        validateLocale(locale);
        user.updateProfile(
                request.firstName() != null ? request.firstName() : user.getFirstName(),
                request.lastName() != null ? request.lastName() : user.getLastName(),
                timezone,
                locale);
        return UserResponse.from(user);
    }

    /**
     * Swaps the display currency. The row is updated and that is it in Phase 1 - the
     * "convert or revalue existing history" choice is Phase 6+ (ADR-0005); the flag is
     * read back by the FX module when it lands.
     */
    @Transactional
    public UserResponse changeBaseCurrency(Long userId, ChangeBaseCurrencyRequest request) {
        User user = requireActive(userId);
        currencyService.requireSupported(request.currencyCode());
        String previous = user.getDefaultCurrencyCode();
        user.changeDefaultCurrency(request.currencyCode());
        java.util.Map<String, Object> before = java.util.Map.of("baseCurrencyCode", previous);
        java.util.Map<String, Object> after = java.util.Map.of("baseCurrencyCode", request.currencyCode());
        auditRecorder.record(AuditEntry.user(userId, "BASE_CURRENCY_CHANGED", "USER", userId, before, after));
        return UserResponse.from(user);
    }

    private User requireActive(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found")
                        .withProperty("userId", userId));
    }

    private static void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown timezone: " + timezone)
                    .withProperty("field", "timezone")
                    .withProperty("timezone", timezone);
        }
    }

    private static void validateLocale(String locale) {
        if (!locale.matches("^[a-z]{2}(-[A-Za-z]{2,4})?$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported locale: " + locale)
                    .withProperty("field", "locale")
                    .withProperty("locale", locale);
        }
        try {
            Locale.forLanguageTag(locale.replace('_', '-'));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported locale: " + locale)
                    .withProperty("field", "locale")
                    .withProperty("locale", locale);
        }
    }
}
