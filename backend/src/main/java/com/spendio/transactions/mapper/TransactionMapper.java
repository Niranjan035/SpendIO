package com.spendio.transactions.mapper;

import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.dto.response.TransferResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Ledger row to {@link TransactionResponse}: nested account/category refs are read from
 * the (lazily loaded) associations, so call inside an open transaction.
 */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction tx, String baseCurrencyCode) {
        TransactionResponse.AccountRef accountRef = new TransactionResponse.AccountRef(
                tx.getAccount().getId(), tx.getAccount().getName(), tx.getAccount().getCurrencyCode());
        TransactionResponse.CategoryRef categoryRef = tx.getCategory() == null ? null
                : new TransactionResponse.CategoryRef(
                        tx.getCategory().getId(), tx.getCategory().getName(), tx.getCategory().getType(),
                        tx.getCategory().getColor(), tx.getCategory().getIcon());
        return new TransactionResponse(
                tx.getId(),
                accountRef,
                categoryRef,
                tx.getType(),
                tx.getDirection(),
                tx.getAmountMinor(),
                tx.getCurrencyCode(),
                tx.getExchangeRate(),
                tx.getBaseAmountMinor(),
                baseCurrencyCode,
                tx.getOccurredOn(),
                tx.getDescription(),
                tx.getMerchant(),
                tx.getPayee(),
                tx.getNotes(),
                tx.getStatus(),
                tx.getTransferGroupId(),
                tx.getReversalOfTransactionId(),
                tx.getReversedAt(),
                tx.getRecurringTransactionId(),
                tx.getCsvImportId(),
                tx.getCreatedAt(),
                tx.getUpdatedAt(),
                tx.getVersion());
    }

    /** Pairs the OUTFLOW/INFLOW legs of one transfer group (ARCHITECTURE.md D.8). */
    public TransferResponse toTransferResponse(String transferGroupId, List<Transaction> legs,
            String baseCurrencyCode) {
        Transaction from = legs.stream()
                .filter(tx -> tx.getDirection() == TransactionDirection.OUTFLOW)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "transfer group " + transferGroupId + " has no OUTFLOW leg"));
        Transaction to = legs.stream()
                .filter(tx -> tx.getDirection() == TransactionDirection.INFLOW)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "transfer group " + transferGroupId + " has no INFLOW leg"));
        return new TransferResponse(transferGroupId, toResponse(from, baseCurrencyCode),
                toResponse(to, baseCurrencyCode));
    }
}
