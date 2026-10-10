package com.spendio.goals.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Create a savings goal (ARCHITECTURE.md D.11). Everything is optional except the
 * name, target amount and currency; {@code milestonePct} defaults to 90 and
 * {@code priority} to 0.
 */
public record CreateGoalRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull @Positive Long targetAmountMinor,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code")
        String currencyCode,
        LocalDate targetDate,
        Long linkedAccountId,
        @Min(0) @Max(5) Integer priority,
        @Size(max = 40) String icon,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "must be a #RRGGBB colour") String color,
        @Min(1) @Max(100) Integer milestonePct) {
}
