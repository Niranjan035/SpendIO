package com.spendio.sharedkernel.events;

/**
 * A ledger write happened for {@code userId} (ARCHITECTURE.md Phase 13): short-lived
 * analytics caches keyed by that user are stale the moment this fires. Published by
 * the transactions module and the CSV import executor; the analytics module listens
 * (kernel events keep the module graph within B.3).
 */
public record LedgerChangedEvent(Long userId) {
}
