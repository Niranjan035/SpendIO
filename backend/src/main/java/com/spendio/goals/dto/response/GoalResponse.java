package com.spendio.goals.dto.response;

import com.spendio.sharedkernel.domain.enums.GoalStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Goal read model (ARCHITECTURE.md D.11): the target/current pair with the derived
 * figures the UI needs - remaining, progress %, days remaining, required-per-month
 * and the on-track verdict. {@code baseProgressPct} is progress recomputed against
 * base-currency amounts (same as {@code progressPct} when the goal is already in the
 * base currency, converted at today's rate otherwise).
 */
public record GoalResponse(
        Long id,
        String name,
        String description,
        long targetAmountMinor,
        long currentAmountMinor,
        long remainingMinor,
        int progressPct,
        String currencyCode,
        int baseProgressPct,
        LocalDate targetDate,
        Long daysRemaining,
        GoalStatus status,
        Long linkedAccountId,
        short priority,
        String icon,
        String color,
        Instant completedAt,
        Long monthlyRequiredMinor,
        Boolean onTrack,
        Integer version,
        Instant createdAt) {
}
