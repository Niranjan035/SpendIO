package com.spendio.transactions.spec;

import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.persistence.SpecificationUtils;
import com.spendio.transactions.domain.Transaction;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria for the ledger search (ARCHITECTURE.md E.6). Account and category are
 * fetched with LEFT joins so ADJUSTMENT rows (no category) survive every filter, and
 * the {@code q} search is a case-insensitive LIKE across the four text columns.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /** Owns the row, excludes soft deletes, then applies every optional filter. */
    public static Specification<Transaction> forFilter(Long userId, TransactionFilter filter, String baseCurrency) {
        return ownedBy(userId)
                .and(SpecificationUtils.notDeleted())
                .and(inIfPresent("type", filter.types()))
                .and(inIfPresent("direction", filter.directions()))
                .and(inIfPresent("status", filter.statuses()))
                .and(inIds("account.id", filter.accountIds()))
                .and(inIds("category.id", filter.categoryIds()))
                .and(equalIfPresent("category.type", filter.categoryType()))
                .and(SpecificationUtils.dateBetween("occurredOn", filter.from(), filter.to()))
                .and(amountBetween(filter))
                .and(SpecificationUtils.equalIfPresent("currencyCode",
                        filter.currencyCode() == null ? null : filter.currencyCode().trim().toUpperCase(Locale.ROOT)))
                .and(baseOnly(filter.baseCurrencyOnly(), baseCurrency))
                .and(notNullIfTrue("recurringTransactionId", filter.recurringOnly()))
                .and(notNullIfTrue("csvImportId", filter.importedOnly()))
                .and(hasNotes(filter.hasNotes()))
                .and(hasReversal(filter.hasReversal()))
                .and(textSearch(filter.q()));
    }

    /** Loads account/category for display; skipped for count/aggregate queries. */
    public static Specification<Transaction> withReferences() {
        return (root, query, cb) -> {
            if (query != null && Long.class.equals(query.getResultType())) {
                return cb.conjunction();
            }
            root.fetch("account", JoinType.LEFT);
            root.fetch("category", JoinType.LEFT);
            query.distinct(true);
            return cb.conjunction();
        };
    }

    public static Specification<Transaction> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    private static <T> Specification<Transaction> inIfPresent(String property, List<T> values) {
        if (values == null || values.isEmpty()) {
            return always();
        }
        return (root, query, cb) -> root.get(property).in(values);
    }

    private static <T> Specification<Transaction> inIds(String path, List<T> ids) {
        if (ids == null || ids.isEmpty()) {
            return always();
        }
        return (root, query, cb) -> {
            String[] parts = path.split("\\.");
            jakarta.persistence.criteria.Path<?> expression = root.get(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                expression = expression.get(parts[i]);
            }
            return expression.in(ids);
        };
    }

    private static <T> Specification<Transaction> equalIfPresent(String path, T value) {
        if (value == null) {
            return always();
        }
        return (root, query, cb) -> {
            String[] parts = path.split("\\.");
            jakarta.persistence.criteria.Path<?> expression = root.get(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                expression = expression.get(parts[i]);
            }
            return cb.equal(expression, value);
        };
    }

    private static Specification<Transaction> amountBetween(TransactionFilter filter) {
        Long min = filter.minAmountMinor();
        Long max = filter.maxAmountMinor();
        if (min == null && max == null) {
            return always();
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (min != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amountMinor"), min));
            }
            if (max != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amountMinor"), max));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<Transaction> baseOnly(Boolean flag, String baseCurrency) {
        if (!Boolean.TRUE.equals(flag) || baseCurrency == null) {
            return always();
        }
        return (root, query, cb) -> cb.equal(root.get("currencyCode"), baseCurrency);
    }

    private static Specification<Transaction> notNullIfTrue(String property, Boolean flag) {
        if (!Boolean.TRUE.equals(flag)) {
            return always();
        }
        return (root, query, cb) -> cb.isNotNull(root.get(property));
    }

    private static Specification<Transaction> hasNotes(Boolean flag) {
        if (!Boolean.TRUE.equals(flag)) {
            return always();
        }
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("notes")), cb.notEqual(root.get("notes"), ""));
    }

    private static Specification<Transaction> hasReversal(Boolean flag) {
        if (!Boolean.TRUE.equals(flag)) {
            return always();
        }
        return (root, query, cb) -> cb.or(
                cb.isNotNull(root.get("reversalOfTransactionId")),
                cb.isNotNull(root.get("reversedAt")));
    }

    private static Specification<Transaction> textSearch(String q) {
        if (q == null || q.isBlank()) {
            return always();
        }
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("description")), pattern, '\\'),
                cb.like(cb.lower(root.get("merchant")), pattern, '\\'),
                cb.like(cb.lower(root.get("payee")), pattern, '\\'),
                cb.like(cb.lower(root.get("notes")), pattern, '\\'));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Specification<Transaction> always() {
        return (root, query, cb) -> cb.conjunction();
    }
}
