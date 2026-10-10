package com.spendio.categories.mapper;

import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
  public CategoryResponse toResponse(Category c) {
    return new CategoryResponse(
        c.getId(),
        c.getUserId(),
        c.getParentId(),
        c.getName(),
        c.getType(),
        c.getIcon(),
        c.getColor(),
        c.isDefault(),
        c.isSystem(),
        c.getSortOrder(),
        c.isExcludeFromBudgets(),
        c.getStatus(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}