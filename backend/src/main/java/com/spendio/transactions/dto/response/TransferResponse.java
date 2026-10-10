package com.spendio.transactions.dto.response;

/**
 * Both legs of one transfer (ARCHITECTURE.md D.8): {@code fromTransaction} is the
 * OUTFLOW leg, {@code toTransaction} the INFLOW leg of the shared
 * {@code transfer_group_id}.
 */
public record TransferResponse(
        String transferGroupId,
        TransactionResponse fromTransaction,
        TransactionResponse toTransaction) {
}
