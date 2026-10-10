package com.spendio.reference.service;

import com.spendio.config.CacheConfig;
import com.spendio.reference.domain.Currency;
import com.spendio.reference.dto.CurrencyResponse;
import com.spendio.reference.repository.CurrencyRepository;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only currency reference access (ARCHITECTURE.md D.4).
 *
 * <p>Both entry points are cached for the lifetime of a release: the table only
 * changes when a migration adds a code, which restarts the application anyway.
 */
@Service
@Transactional(readOnly = true)
public class CurrencyService {

    private final CurrencyRepository currencyRepository;

    public CurrencyService(CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @Cacheable(cacheNames = CacheConfig.CURRENCIES, key = "'active'")
    public List<CurrencyResponse> listActive() {
        return currencyRepository.findAllActive().stream().map(CurrencyResponse::from).toList();
    }

    @Cacheable(cacheNames = CacheConfig.CURRENCIES, key = "'code:' + #code")
    public CurrencyResponse get(String code) {
        return currencyRepository.findById(normalise(code))
                .map(CurrencyResponse::from)
                .orElseThrow(() -> notFound(code));
    }

    /**
     * Guard used by every module that stamps a currency onto a row: the code must exist and
     * still be offered to users, otherwise the write would create money in a currency nobody
     * can price.
     */
    public Currency requireSupported(String code) {
        Currency currency = currencyRepository.findById(normalise(code))
                .orElseThrow(() -> notFound(code));
        if (!currency.isActive()) {
            throw new BusinessException(ErrorCode.CURRENCY_NOT_SUPPORTED, "Currency " + code + " is no longer active")
                    .withProperty("currencyCode", currency.getCode());
        }
        return currency;
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static BusinessException notFound(String code) {
        return new BusinessException(ErrorCode.CURRENCY_NOT_FOUND, "Unknown currency: " + code)
                .withProperty("currencyCode", code);
    }
}
