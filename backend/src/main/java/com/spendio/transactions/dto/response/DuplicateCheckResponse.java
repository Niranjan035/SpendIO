package com.spendio.transactions.dto.response;

/**
 * Duplicate probe answer (ARCHITECTURE.md D.8): {@code HIGH} = identical description
 * found, {@code LOW} = same date/amount/currency only, {@code NONE} = nothing close.
 */
public record DuplicateCheckResponse(
        boolean duplicate,
        Long matchedTransactionId,
        Confidence confidence,
        String reason) {

    public enum Confidence {
        HIGH,
        LOW,
        NONE
    }
}
