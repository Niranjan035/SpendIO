package com.spendio.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.domain.DecimalStyle;
import com.spendio.users.domain.SymbolPosition;
import com.spendio.users.domain.Theme;
import com.spendio.users.domain.UserPreference;
import com.spendio.users.dto.request.UpdatePreferencesRequest;
import com.spendio.users.dto.response.UserPreferenceResponse;
import com.spendio.users.repository.UserPreferenceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @InjectMocks
    private PreferenceService preferenceService;

    @Test
    void getReturnsStoredDefaultsForASeededUser() {
        when(userPreferenceRepository.findByUserId(7L)).thenReturn(Optional.of(defaultsFor(7L)));

        UserPreferenceResponse response = preferenceService.get(7L);

        assertThat(response.theme()).isEqualTo(Theme.SYSTEM);
        assertThat(response.budgetAlertThresholdPct()).isEqualTo(80);
        assertThat(response.goalReminderDays()).isEqualTo(30);
        assertThat(response.recurringReminderDays()).isEqualTo(3);
        assertThat(response.emailDigestEnabled()).isFalse();
    }

    @Test
    void getRejectsUserWithoutPreferenceRow() {
        when(userPreferenceRepository.findByUserId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> preferenceService.get(7L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateReplacesEveryPreference() {
        UserPreference stored = defaultsFor(7L);
        when(userPreferenceRepository.findByUserId(7L)).thenReturn(Optional.of(stored));
        when(userPreferenceRepository.save(stored)).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferenceResponse response = preferenceService.update(7L, validRequest()
                .withTheme(Theme.DARK)
                .withDecimalStyle(DecimalStyle.COMMA)
                .withSymbolPosition(SymbolPosition.AFTER)
                .withThreshold(95)
                .withGoalDays(7)
                .withRecurringDays(1)
                .withEmailDigest(true)
                .withReducedMotion(true)
                .build());

        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.decimalStyle()).isEqualTo(DecimalStyle.COMMA);
        assertThat(response.currencySymbolPosition()).isEqualTo(SymbolPosition.AFTER);
        assertThat(response.budgetAlertThresholdPct()).isEqualTo(95);
        assertThat(response.goalReminderDays()).isEqualTo(7);
        assertThat(response.recurringReminderDays()).isEqualTo(1);
        assertThat(response.emailDigestEnabled()).isTrue();
        assertThat(response.reducedMotion()).isTrue();
        assertThat(stored.getTheme()).isEqualTo(Theme.DARK);
    }

    @Test
    void updateRejectsThresholdOutsideOneToHundred() {
        assertThatThrownBy(() -> preferenceService.update(7L, validRequest().withThreshold(0).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(business.getProperties()).containsEntry("field", "budgetAlertThresholdPct");
                });

        assertThatThrownBy(() -> preferenceService.update(7L, validRequest().withThreshold(101).build()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateRejectsReminderWindowsOutsideTheirRanges() {
        assertThatThrownBy(() -> preferenceService.update(7L, validRequest().withGoalDays(366).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getProperties())
                        .containsEntry("field", "goalReminderDays"));

        assertThatThrownBy(() -> preferenceService.update(7L, validRequest().withRecurringDays(31).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getProperties())
                        .containsEntry("field", "recurringReminderDays"));
    }

    @Test
    void updateRejectsFirstDayOutsideOneToSeven() {
        assertThatThrownBy(() -> preferenceService.update(7L, validRequest().withFirstDayOfWeek(9).build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getProperties())
                        .containsEntry("field", "firstDayOfWeek"));
    }

    private static UserPreference defaultsFor(Long userId) {
        return UserPreference.defaultsFor(userId);
    }

    private static RequestBuilder validRequest() {
        return new RequestBuilder();
    }

    /** Small builder so each test only states the preference it is actually exercising. */
    private static final class RequestBuilder {
        private Theme theme = Theme.SYSTEM;
        private String dateFormat = "yyyy-MM-dd";
        private int firstDayOfWeek = 1;
        private DecimalStyle decimalStyle = DecimalStyle.DOT;
        private String groupSeparator = ",";
        private SymbolPosition symbolPosition = SymbolPosition.BEFORE;
        private String numberFormat = "#,##0.00";
        private int threshold = 80;
        private int goalDays = 30;
        private int recurringDays = 3;
        private boolean emailDigest;
        private boolean reducedMotion;

        RequestBuilder withTheme(Theme value) {
            this.theme = value;
            return this;
        }

        RequestBuilder withFirstDayOfWeek(int value) {
            this.firstDayOfWeek = value;
            return this;
        }

        RequestBuilder withDecimalStyle(DecimalStyle value) {
            this.decimalStyle = value;
            return this;
        }

        RequestBuilder withSymbolPosition(SymbolPosition value) {
            this.symbolPosition = value;
            return this;
        }

        RequestBuilder withThreshold(int value) {
            this.threshold = value;
            return this;
        }

        RequestBuilder withGoalDays(int value) {
            this.goalDays = value;
            return this;
        }

        RequestBuilder withRecurringDays(int value) {
            this.recurringDays = value;
            return this;
        }

        RequestBuilder withEmailDigest(boolean value) {
            this.emailDigest = value;
            return this;
        }

        RequestBuilder withReducedMotion(boolean value) {
            this.reducedMotion = value;
            return this;
        }

        UpdatePreferencesRequest build() {
            return new UpdatePreferencesRequest(
                    theme, dateFormat, firstDayOfWeek, decimalStyle, groupSeparator, symbolPosition,
                    numberFormat, threshold, goalDays, recurringDays, emailDigest, reducedMotion);
        }
    }
}
