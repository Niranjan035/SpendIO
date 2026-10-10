package com.spendio.categories.service;

import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.dto.request.UpdateCategoryRequest;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.dto.response.CategoryTreeResponse;
import com.spendio.categories.dto.response.CategoryUsageResponse;
import com.spendio.categories.mapper.CategoryMapper;
import com.spendio.categories.repository.CategoryRepository;
import com.spendio.categories.spec.CategoryFilter;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.PageResponse;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryQueryService {
  private final CategoryRepository categories;
  private final CategoryMapper mapper;

  public CategoryQueryService(CategoryRepository categories, CategoryMapper mapper) {
    this.categories = categories;
    this.mapper = mapper;
  }

  public PageResponse<CategoryResponse> list(Long userId, Integer page, Integer size, String sort, CategoryFilter filter) {
    Pageable pageable = PageRequestFactory.of(page, size, sort, List.of("name", "sortOrder", "createdAt"));
    Page<CategoryResponse> p = categories.findAll(filter.toSpecification(userId), pageable).map(mapper::toResponse);
    return PageResponse.from(p);
  }

  public CategoryTreeResponse tree(Long userId, Category.Type type) {
    List<Category> roots = categories.findByUserIdAndParentIdIsNullAndDeletedAtIsNullAndStatusOrderBySortOrderAsc(userId, Category.Status.ACTIVE);
    roots = roots.stream().filter(r -> type == null || r.getType() == type).sorted(Comparator.comparing(r -> r.getSortOrder() == null ? 0 : r.getSortOrder())).toList();
    return buildTree(userId, roots);
  }

  private CategoryTreeResponse buildTree(Long userId, List<Category> roots) {
    List<CategoryTreeResponse> childrenList = new ArrayList<>();
    for (Category root : roots) {
      List<Category> kids = categories.findByUserIdAndParentIdAndDeletedAtIsNullAndStatusOrderBySortOrderAsc(userId, root.getId(), Category.Status.ACTIVE);
      kids = kids.stream().sorted(Comparator.comparing(k -> k.getSortOrder() == null ? 0 : k.getSortOrder())).toList();
      List<CategoryTreeResponse> kidNodes = kids.stream().map(k -> new CategoryTreeResponse(mapper.toResponse(k), List.of())).toList();
      childrenList.add(new CategoryTreeResponse(mapper.toResponse(root), kidNodes));
    }
    return new CategoryTreeResponse(null, childrenList);
  }

  public CategoryUsageResponse usage(Long userId, Long id, LocalDate from, LocalDate to) {
    return new CategoryUsageResponse(id, from, to, 0, 0, List.of());
  }

  public CategoryResponse detail(Long userId, Long id) {
    Category c = categories.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, "Category not found").withProperty("id", id));
    return mapper.toResponse(c);
  }

  /**
   * Every non-deleted category of the user in one read - the scope input for budget
   * progress (ARCHITECTURE.md D.10), which must see parents, children and flagged rows
   * alike to resolve subcategory inclusion and the exclude_from_budgets rule.
   */
  public List<CategoryResponse> allActive(Long userId) {
    return categories.findByUserIdAndDeletedAtIsNull(userId).stream().map(mapper::toResponse).toList();
  }
}