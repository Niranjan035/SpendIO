package com.spendio.categories.service;

import com.spendio.categories.domain.Category;
import com.spendio.categories.repository.CategoryRepository;
import com.spendio.categories.support.DefaultCategoryCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCategorySeeder {
  private final CategoryRepository repo;
  private final DefaultCategoryCatalog catalog;

  public DefaultCategorySeeder(CategoryRepository repo, DefaultCategoryCatalog catalog) {
    this.repo = repo;
    this.catalog = catalog;
  }

  @Transactional
  public void seedForUser(Long userId) {
    if (userId == null) return;
    catalog.getAll().forEach(item -> {
      Long parentId = item.parentId();
      if (parentId == null) {
        boolean exists = repo.existsByUserIdAndParentIdAndNameAndDeletedAtIsNull(userId, null, item.name());
        if (!exists) {
          Category c = Category.create(userId, item.name(), item.type(), null, item.icon(), item.color(), item.excludeFromBudgets());
          c.setDefault(true);
          c.setSystem(false);
          repo.save(c);
        }
      }
    });
  }
}