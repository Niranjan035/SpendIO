package com.spendio.accounts.support;

import java.time.LocalDate;

/**
 * Daily signed sum of ledger rows for the balance series (ARCHITECTURE.md D.6). Lives in
 * support (not ..repository..) so the "repositories are interfaces" ArchUnit rule holds.
 */
public record DailyNet(LocalDate date, long netMinor) {
}