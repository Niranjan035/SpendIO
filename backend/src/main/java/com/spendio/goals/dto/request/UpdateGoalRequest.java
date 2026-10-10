package com.spendio.goals.dto.request;

import com.spendio.sharedkernel.domain.enums.GoalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Partial goal edit (ARCHITECTURE.md D.11): absent fields stay unchanged, the two
 * {@code clear*} flags explicitly null out {@code target_date}/
 * {@code linked_account_id}, and {@code version} guards against a concurrent edit.
 * {@code status} only moves between ACTIVE/PAUSED/CANCELLED - completion is the
 * dedicated {@code /complete} endpoint (or automatic at target).
 */
public record UpdateGoalRequest(
        @Size(max = 80) String name,
        @Size(max = 500) String description,
        @Positive Long targetAmountMinor,
        LocalDate targetDate,
        Boolean clearTargetDate,
        Long linkedAccountId,
        Boolean clearLinkedAccount,
        @Min(0) @Max(5) Integer priority,
        @Size(max = 40) String icon,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "must be a #RRGGBB colour") String color,
        @Min(1) @Max(100) Integer milestonePct,
        GoalStatus status,
        Integer version) {
}
