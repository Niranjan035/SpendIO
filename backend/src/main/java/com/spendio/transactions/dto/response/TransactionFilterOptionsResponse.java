package com.spendio.transactions.dto.response;

import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Filter sidebar data (ARCHITECTURE.md D.8) for the transactions search screen. */
public record TransactionFilterOptionsResponse(
        List<AccountOption> accounts,
        List<CategoryResponse> categories,
        List<String> currencies,
        LocalDate minDate,
        LocalDate maxDate,
        Map<TransactionType, Long> typeCounts) {

    public record AccountOption(Long id, String name, String currencyCode, AccountStatus status) {
    }
}
