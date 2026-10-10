package com.spendio.categories.service;

import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.dto.request.UpdateCategoryRequest;
import com.spendio.categories.repository.CategoryRepository;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {
  private final CategoryRepository categories;

  public CategoryService(CategoryRepository categories) {
    this.categories = categories;
  }

  @Transactional
  public Category create(Long userId, CreateCategoryRequest req) {
    Category.Type type = Category.Type.valueOf(req.type().toUpperCase());
    Long parentId = req.parentId();
    if (parentId != null) {
      Category parent = categories.findByIdAndUserIdAndDeletedAtIsNull(parentId, userId)
          .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Parent not found").withProperty("id", parentId));
      if (parent.getType() != type) {
        throw new BusinessException(ErrorCode.TRANSACTION_CATEGORY_TYPE_MISMATCH, "Parent type mismatch");
      }
      if (parent.getParentId() != null) {
        throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY, "Category depth cannot exceed 2");
      }
    }
    String name = req.name().trim();
    if (categories.existsByUserIdAndParentIdAndNameAndDeletedAtIsNull(userId, parentId, name)) {
      throw new BusinessException(ErrorCode.CONFLICT, "Category name already exists");
    }
    Category c = Category.create(userId, name, type, parentId, req.icon(), req.color(), Boolean.TRUE.equals(req.excludeFromBudgets()));
    return categories.save(c);
  }

  @Transactional
  public Category update(Long userId, Long id, UpdateCategoryRequest req) {
    Category c = categories.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found").withProperty("id", id));
    if (c.isSystem()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "System categories are read-only");
    }
    String name = req.name().trim();
    if (!name.equals(c.getName()) && categories.existsByUserIdAndParentIdAndNameAndDeletedAtIsNullAndIdNot(userId, c.getParentId(), name, id)) {
      throw new BusinessException(ErrorCode.CONFLICT, "Category name already exists");
    }
    c.setName(name);
    c.setIcon(req.icon());
    c.setColor(req.color());
    c.setExcludeFromBudgets(Boolean.TRUE.equals(req.excludeFromBudgets()));
    return categories.save(c);
  }

  @Transactional
  public void delete(Long userId, Long id, Long reassignTo) {
    Category c = categories.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found").withProperty("id", id));
    if (c.isSystem()) {
      throw new BusinessException(ErrorCode.FORBIDDEN, "System categories are read-only");
    }
    if (reassignTo == null && categories.countByUserIdAndParentIdAndDeletedAtIsNull(userId, id) > 0) {
      throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "Category has subcategories");
    }
    // reassignTo logic minimal for scaffold
    c.softDelete();
    categories.save(c);
  }

  @Transactional
  public Category archive(Long userId, Long id) {
    Category c = categories.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found").withProperty("id", id));
    c.markArchived();
    return categories.save(c);
  }

  @Transactional
  public Category restore(Long userId, Long id) {
    Category c = categories.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found").withProperty("id", id));
    c.markRestored();
    return categories.save(c);
  }

  @Transactional
  public void reorder(Long userId, com.spendio.categories.dto.request.ReorderCategoriesRequest req) {
    if (req == null || req.items() == null) return;
    req.items().forEach(item -> categories.findByIdAndUserIdAndDeletedAtIsNull(item.id(), userId).ifPresent(c -> {
      c.setSortOrder(item.sortOrder() == null ? 0 : item.sortOrder().shortValue());
    }));
  }
}