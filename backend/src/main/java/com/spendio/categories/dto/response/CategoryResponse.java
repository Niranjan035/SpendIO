package com.spendio.categories.dto.response;

import com.spendio.categories.domain.Category;

public record CategoryResponse(
    Long id,
    Long userId,
    Long parentId,
    String name,
    Category.Type type,
    String icon,
    String color,
    boolean isDefault,
    boolean isSystem,
    int sortOrder,
    boolean excludeFromBudgets,
    Category.Status status,
    java.time.Instant archivedAt,
    java.time.Instant createdAt,
    java.time.Instant updatedAt) { }