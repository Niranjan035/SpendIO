package com.spendio.recurring.spec;

import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.persistence.SpecificationUtils;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria for the recurring list (ARCHITECTURE.md D.9 {@code RecurringQueryService.list}).
 * {@code dueWithinDays} means "due on or before today + N" - overdue rules are due too.
 */
public final class RecurringSpecifications {

    private RecurringSpecifications() {
    }

    /** Owns the row, excludes soft deletes, then applies every optional filter. */
    public static Specification<RecurringTransaction> forFilter(Long userId, RecurringFilter filter, LocalDate today) {
        return ownedBy(userId)
                .and(SpecificationUtils.notDeleted())
                .and(SpecificationUtils.equalIfPresent("status", filter.status()))
                .and(SpecificationUtils.equalIfPresent("type", filter.type()))
                .and(SpecificationUtils.equalIfPresent("account.id", filter.accountId()))
                .and(SpecificationUtils.equalIfPresent("category.id", filter.categoryId()))
                .and(dueWithin(filter.dueWithinDays(), today));
    }

    /** Loads account/to-account/category for display; skipped for count queries. */
    public static Specification<RecurringTransaction> withReferences() {
        return (root, query, cb) -> {
            if (query != null && Long.class.equals(query.getResultType())) {
                return cb.conjunction();
            }
            root.fetch("account", JoinType.LEFT);
            root.fetch("toAccount", JoinType.LEFT);
            root.fetch("category", JoinType.LEFT);
            query.distinct(true);
            return cb.conjunction();
        };
    }

    public static Specification<RecurringTransaction> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    /**
     * Dashboard "due soon" set (ARCHITECTURE.md E.9): ACTIVE rules whose next occurrence
     * is on or before {@code today + days}; overdue rules are due too, so there is no
     * lower bound. {@code days} is clamped 1..90 by the caller.
     */
    public static Specification<RecurringTransaction> upcoming(Long userId, LocalDate today, int days) {
        return ownedBy(userId)
                .and(SpecificationUtils.notDeleted())
                .and((root, query, cb) -> cb.equal(root.get("status"), RecurringStatus.ACTIVE))
                .and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("nextRunDate"), today.plusDays(days)))
                .and(withReferences());
    }

    /** {@code nextRunDate <= today + dueWithinDays}; no lower bound, so overdue shows. */
    private static Specification<RecurringTransaction> dueWithin(Integer dueWithinDays, LocalDate today) {
        if (dueWithinDays == null || dueWithinDays < 0 || today == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("nextRunDate"), today.plusDays(dueWithinDays));
    }
}
