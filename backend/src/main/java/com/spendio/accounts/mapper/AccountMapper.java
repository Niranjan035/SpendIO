package com.spendio.accounts.mapper;

import com.spendio.accounts.domain.Account;
import com.spendio.accounts.dto.response.AccountResponse;

/**
 * Entity to response mapping for accounts (ARCHITECTURE.md D.6). No Lombok or
 * MapStruct in the codebase - plain static mapping like the other modules.
 */
public final class AccountMapper {

    private AccountMapper() {
    }

    /**
     * @param availableCreditMinor credit line headroom, null except for CREDIT_CARD
     */
    public static AccountResponse toResponse(Account account, long balanceMinor, long balanceBaseMinor,
            String baseCurrencyCode, Long availableCreditMinor, long transactionCount) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrencyCode(),
                account.getOpeningBalanceMinor(),
                account.getOpeningBalanceDate(),
                account.getInstitution(),
                account.getLast4(),
                account.getCreditLimitMinor(),
                account.getBillingDay(),
                account.getDueDay(),
                account.getIcon(),
                account.getColor(),
                account.getNotes(),
                account.isPrimary(),
                account.getStatus(),
                transactionCount,
                account.getCreatedAt(),
                balanceMinor,
                balanceBaseMinor,
                baseCurrencyCode,
                availableCreditMinor);
    }
}