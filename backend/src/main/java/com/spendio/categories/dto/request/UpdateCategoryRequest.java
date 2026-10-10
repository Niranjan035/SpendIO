package com.spendio.categories.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    @NotBlank @Size(min = 2, max = 60) String name,
    @Size(max = 40) String icon,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
    Boolean excludeFromBudgets) { }