package com.spendio.categories.export;

import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.export.DataExporter;
import org.springframework.stereotype.Component;

/**
 * Categories section of a user's data export (ARCHITECTURE.md Phase 14). The shared
 * system categories ({@code user_id NULL}) are deliberately excluded - they are not
 * personal data.
 */
@Component
public class CategoriesDataExporter implements DataExporter {

    private final CategoryQueryService categories;

    public CategoriesDataExporter(CategoryQueryService categories) {
        this.categories = categories;
    }

    @Override
    public String section() {
        return "categories";
    }

    @Override
    public Object exportFor(Long userId) {
        return categories.allActive(userId);
    }
}
