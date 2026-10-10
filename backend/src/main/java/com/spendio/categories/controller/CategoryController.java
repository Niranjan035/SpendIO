package com.spendio.categories.controller;

import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.dto.request.ReorderCategoriesRequest;
import com.spendio.categories.dto.request.UpdateCategoryRequest;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.dto.response.CategoryTreeResponse;
import com.spendio.categories.dto.response.CategoryUsageResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.categories.service.CategoryService;
import com.spendio.categories.service.DefaultCategorySeeder;
import com.spendio.categories.spec.CategoryFilter;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
  private final CategoryService service;
  private final CategoryQueryService query;
  private final DefaultCategorySeeder seeder;

  public CategoryController(CategoryService service, CategoryQueryService query, DefaultCategorySeeder seeder) {
    this.service = service;
    this.query = query;
    this.seeder = seeder;
  }

  @GetMapping
  public PageResponse<CategoryResponse> list(
      @CurrentUserId Long userId,
      @RequestParam(required = false) Category.Type type,
      @RequestParam(required = false) Category.Status status,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean flat,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    return query.list(userId, page, size, sort, new CategoryFilter(type, status, q));
  }

  @GetMapping("/tree")
  public CategoryTreeResponse tree(@CurrentUserId Long userId, @RequestParam Category.Type type) {
    return query.tree(userId, type);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryResponse create(@CurrentUserId Long userId, @Valid @RequestBody CreateCategoryRequest req) {
    return query.detail(userId, service.create(userId, req).getId());
  }

  @GetMapping("/{id}")
  public CategoryResponse detail(@CurrentUserId Long userId, @PathVariable Long id) {
    return query.detail(userId, id);
  }

  @PatchMapping("/{id}")
  public CategoryResponse update(@CurrentUserId Long userId, @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest req) {
    return query.detail(userId, service.update(userId, id, req).getId());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@CurrentUserId Long userId, @PathVariable Long id, @RequestParam(required = false) Long reassignTo) {
    service.delete(userId, id, reassignTo);
  }

  @PostMapping("/{id}/archive")
  public CategoryResponse archive(@CurrentUserId Long userId, @PathVariable Long id) {
    return query.detail(userId, service.archive(userId, id).getId());
  }

  @PostMapping("/{id}/restore")
  public CategoryResponse restore(@CurrentUserId Long userId, @PathVariable Long id) {
    return query.detail(userId, service.restore(userId, id).getId());
  }

  @GetMapping("/{id}/usage")
  public CategoryUsageResponse usage(@CurrentUserId Long userId, @PathVariable Long id, @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return query.usage(userId, id, from, to);
  }

  @PostMapping("/reorder")
  public void reorder(@CurrentUserId Long userId, @Valid @RequestBody ReorderCategoriesRequest req) {
    service.reorder(userId, req);
  }

  @PostMapping("/seed-defaults")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void seedDefaults(@CurrentUserId Long userId) {
    seeder.seedForUser(userId);
  }
}