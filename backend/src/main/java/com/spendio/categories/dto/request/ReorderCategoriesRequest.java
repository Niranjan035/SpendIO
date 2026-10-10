package com.spendio.categories.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderCategoriesRequest(@Valid List<Item> items) {
  public record Item(@NotNull Long id, @NotNull Integer sortOrder) { }
}