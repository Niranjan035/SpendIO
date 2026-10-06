package com.spendio.sharedkernel.persistence;

import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Small, composable criteria helpers so every module writes filters the same way.
 * All filters are AND-ed and always combined with {@code user_id} in the calling service
 * (ARCHITECTURE.md C.2, B.3).
 */
public final class SpecificationUtils {

    private SpecificationUtils() {
    }

    /** Excludes soft-deleted rows. */
    public static <T extends SoftDeletableEntity> Specification<T> notDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }

    /** Matches when {@code value} is null (a no-op filter for optional query params). */
    public static <T> Specification<T> equalIfPresent(String property, Object value) {
        return (root, query, criteriaBuilder) ->
                value == null ? criteriaBuilder.conjunction() : criteriaBuilder.equal(root.get(property), value);
    }

    /** Inclusive date window, either bound optional. */
    public static <T> Specification<T> dateBetween(String property, LocalDate from, LocalDate to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(property), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(property), to));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Applies {@code other} only when {@code condition} holds - for optional filters. */
    public static <T> Specification<T> andWhen(boolean condition, Specification<T> other) {
        if (!condition) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        return other;
    }

    /** Text contains, case-insensitive; {@code null} value matches everything. */
    public static <T> Specification<T> containsIgnoreCase(String property, String value) {
        return (root, query, criteriaBuilder) -> {
            if (value == null || value.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            Expression<String> expression = root.get(property).as(String.class);
            return criteriaBuilder.like(criteriaBuilder.lower(expression),
                    "%" + value.trim().toLowerCase() + "%");
        };
    }
}
