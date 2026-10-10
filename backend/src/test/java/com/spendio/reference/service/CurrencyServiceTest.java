package com.spendio.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.spendio.reference.domain.Currency;
import com.spendio.reference.dto.CurrencyResponse;
import com.spendio.reference.repository.CurrencyRepository;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private CurrencyService currencyService;

    private static final Currency EUR = new Currency("EUR", "Euro", 2, "€", true, 2);
    private static final Currency HRK = new Currency("HRK", "Croatian Kuna", 2, "kn", false, 55);

    @Test
    void listActiveMapsInSortOrder() {
        when(currencyRepository.findAllActive()).thenReturn(List.of(EUR));

        List<CurrencyResponse> currencies = currencyService.listActive();

        assertThat(currencies).containsExactly(
                new CurrencyResponse("EUR", "Euro", 2, "€", true));
    }

    @Test
    void getLooksUpCaseInsensitively() {
        when(currencyRepository.findById("EUR")).thenReturn(Optional.of(EUR));

        CurrencyResponse response = currencyService.get(" eur ");

        assertThat(response.code()).isEqualTo("EUR");
        assertThat(response.minorUnit()).isEqualTo(2);
    }

    @Test
    void getRejectsUnknownCode() {
        when(currencyRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.get("XXX"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CURRENCY_NOT_FOUND));
    }

    @Test
    void requireSupportedAcceptsActiveCurrency() {
        when(currencyRepository.findById("EUR")).thenReturn(Optional.of(EUR));

        assertThat(currencyService.requireSupported("eur")).isSameAs(EUR);
    }

    @Test
    void requireSupportedRejectsInactiveCurrency() {
        when(currencyRepository.findById("HRK")).thenReturn(Optional.of(HRK));

        assertThatThrownBy(() -> currencyService.requireSupported("HRK"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CURRENCY_NOT_SUPPORTED);
    }

    @Test
    void requireSupportedRejectsUnknownCurrency() {
        when(currencyRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyService.requireSupported("XXX"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CURRENCY_NOT_FOUND);
    }
}
