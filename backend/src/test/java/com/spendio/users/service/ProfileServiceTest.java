package com.spendio.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.reference.domain.Currency;
import com.spendio.reference.service.CurrencyService;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.domain.User;
import com.spendio.users.dto.request.ChangeBaseCurrencyRequest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.request.UpdateProfileRequest;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.repository.UserPreferenceRepository;
import com.spendio.users.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private CurrencyService currencyService;

    @Mock
    private AuditRecorder auditRecorder;

    @InjectMocks
    private ProfileService profileService;

    private static final Currency EUR = new Currency("EUR", "Euro", 2, "€", true, 2);

    @Test
    void createStoresNormalisedEmailAndSeedsPreferences() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(currencyService.requireSupported("USD")).thenReturn(new Currency("USD", "US Dollar", 2, "$", true, 1));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = profileService.create(new CreateUserCommand(
                "  Ana@Example.COM ", "{bcrypt}", "Ana", "Lopes", "Europe/Lisbon", "en-GB", "usd"));

        assertThat(response.email()).isEqualTo("ana@example.com");
        assertThat(response.displayName()).isEqualTo("Ana Lopes");
        assertThat(response.timezone()).isEqualTo("Europe/Lisbon");
        assertThat(response.baseCurrencyCode()).isEqualTo("USD");
        assertThat(response.emailVerified()).isFalse();
        verify(userPreferenceRepository).save(any());
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> profileService.create(new CreateUserCommand(
                "Ana@example.com", "{bcrypt}", null, null, "UTC", "en-GB", "USD")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_EMAIL_TAKEN);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUnknownTimezone() {
        assertThatThrownBy(() -> profileService.create(new CreateUserCommand(
                "ana@example.com", "{bcrypt}", null, null, "Nowhere/Land", "en-GB", "USD")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(business.getProperties()).containsEntry("field", "timezone");
                });
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsInactiveCurrency() {
        when(currencyService.requireSupported("HRK"))
                .thenThrow(new BusinessException(ErrorCode.CURRENCY_NOT_SUPPORTED, "Currency HRK is no longer active"));

        assertThatThrownBy(() -> profileService.create(new CreateUserCommand(
                "ana@example.com", "{bcrypt}", null, null, "UTC", "en-GB", "HRK")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CURRENCY_NOT_SUPPORTED);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void getReturnsProfileForKnownUser() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

        UserResponse response = profileService.get(7L);

        assertThat(response.email()).isEqualTo("ana@example.com");
        assertThat(response.displayName()).isEqualTo("Ana Lopes");
    }

    @Test
    void getRejectsUnknownOrDeletedUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.get(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateKeepsFieldsLeftNullAndRederivesDisplayName() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

        UserResponse response = profileService.update(7L, new UpdateProfileRequest("Anita", null, null, null));

        assertThat(response.firstName()).isEqualTo("Anita");
        assertThat(response.lastName()).isEqualTo("Lopes");
        assertThat(response.displayName()).isEqualTo("Anita Lopes");
        assertThat(response.timezone()).isEqualTo("Europe/Lisbon");
        assertThat(response.locale()).isEqualTo("en-GB");
    }

    @Test
    void updateRejectsUnknownLocale() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> profileService.update(7L, new UpdateProfileRequest(null, null, null, "english")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getProperties())
                        .containsEntry("field", "locale"));
    }

    @Test
    void changeBaseCurrencyUpdatesTheProfile() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(currencyService.requireSupported("EUR")).thenReturn(EUR);

        UserResponse response =
                profileService.changeBaseCurrency(7L, new ChangeBaseCurrencyRequest("eur", false));

        assertThat(response.baseCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void changeBaseCurrencyRejectsUnknownCurrency() {
        User user = savedUser();
        when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));
        when(currencyService.requireSupported("XXX"))
                .thenThrow(new BusinessException(ErrorCode.CURRENCY_NOT_FOUND, "Unknown currency: XXX"));

        assertThatThrownBy(() -> profileService.changeBaseCurrency(7L, new ChangeBaseCurrencyRequest("XXX", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CURRENCY_NOT_FOUND);
        assertThat(user.getDefaultCurrencyCode()).isEqualTo("USD");
    }

    private static User savedUser() {
        User user = User.create("ana@example.com", "{bcrypt}", "Europe/Lisbon", "en-GB", "USD");
        user.updateProfile("Ana", "Lopes", "Europe/Lisbon", "en-GB");
        return user;
    }
}
