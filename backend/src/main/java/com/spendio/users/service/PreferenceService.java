package com.spendio.users.service;

import com.spendio.users.domain.UserPreference;
import com.spendio.users.dto.request.UpdatePreferencesRequest;
import com.spendio.users.dto.response.UserPreferenceResponse;
import com.spendio.users.mapper.UserPreferenceMapper;
import com.spendio.users.repository.UserPreferenceRepository;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Display and notification preferences for the authenticated account.
 *
 * <p>The row is created with the user (C.3 #2), so a missing row is a data problem and
 * surfaces as a 404 rather than being silently invented on read.
 */
@Service
public class PreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;

    public PreferenceService(UserPreferenceRepository userPreferenceRepository) {
        this.userPreferenceRepository = userPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public UserPreferenceResponse get(Long userId) {
        return UserPreferenceMapper.toResponse(require(userId));
    }

    /** Replaces every stored preference with the payload (PUT semantics, E.3). */
    @Transactional
    public UserPreferenceResponse update(Long userId, UpdatePreferencesRequest request) {
        requireRanges(request);
        UserPreference preference = UserPreferenceMapper.toEntity(require(userId), request);
        return UserPreferenceMapper.toResponse(userPreferenceRepository.save(preference));
    }

    private UserPreference require(Long userId) {
        return userPreferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Preferences not found")
                        .withProperty("userId", userId));
    }

    /** Ranges mirror the DDL CHECK constraints so a bad call fails before hitting MySQL. */
    private static void requireRanges(UpdatePreferencesRequest request) {
        requireInRange("budgetAlertThresholdPct", request.budgetAlertThresholdPct(), 1, 100);
        requireInRange("goalReminderDays", request.goalReminderDays(), 0, 365);
        requireInRange("recurringReminderDays", request.recurringReminderDays(), 0, 30);
        requireInRange("firstDayOfWeek", request.firstDayOfWeek(), 1, 7);
    }

    private static void requireInRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    field + " must be between " + min + " and " + max)
                    .withProperty("field", field)
                    .withProperty("value", value);
        }
    }
}
