package com.spendio.budgets.spec;

import com.spendio.budgets.domain.Budget;
import com.spendio.sharedkernel.persistence.SpecificationUtils;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/** Criteria for {@link BudgetFilter}; always composed with {@code userId} by the caller. */
public final class BudgetSpecifications {

    private BudgetSpecifications() {
    }

    public static Specification<Budget> forFilter(Long userId, BudgetFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(SpecificationUtils.<Budget>notDeleted().toPredicate(root, query, cb));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.periodType() != null) {
                predicates.add(cb.equal(root.get("periodType"), filter.periodType()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.activeInPeriod() != null) {
                LocalDate day = filter.activeInPeriod();
                predicates.add(cb.lessThanOrEqualTo(root.get("periodStart"), day));
                predicates.add(cb.greaterThanOrEqualTo(root.get("periodEnd"), day));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Fetch-joins the lazy category so list/detail mapping never triggers N+1 reads. */
    public static Specification<Budget> withCategory() {
        return (root, query, cb) -> {
            root.fetch("category", JoinType.LEFT);
            if (query != null) {
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }
}
