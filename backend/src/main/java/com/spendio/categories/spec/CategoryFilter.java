package com.spendio.categories.spec;

import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.persistence.SpecificationUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public record CategoryFilter(Category.Type type, Category.Status status, String q) {

  public Specification<Category> toSpecification(Long userId) {
    return (root, query, cb) -> {
      Predicate p = cb.isNull(root.get("deletedAt"));
      p = cb.and(p, cb.equal(root.get("userId"), userId));
      if (type != null) p = cb.and(p, cb.equal(root.get("type"), type));
      if (status != null) p = cb.and(p, cb.equal(root.get("status"), status));
      if (q != null && !q.isBlank()) {
        String like = "%" + q.trim().toLowerCase() + "%";
        p = cb.and(p, cb.like(cb.lower(root.get("name")), like));
      }
      return p;
    };
  }
}