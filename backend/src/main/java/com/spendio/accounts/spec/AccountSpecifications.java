package com.spendio.accounts.spec;

import com.spendio.accounts.domain.Account;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query builder for the account list (ARCHITECTURE.md D.6). Filters are whitelisted
 * and bound by JPA criteria; {@code hasTransactions} is deliberately absent - it would
 * need a subquery against the transactions table, which this module must not reach -
 * and is handled by the native matching-id query in list orchestration instead.
 */
public final class AccountSpecifications {

    private AccountSpecifications() {
    }

    public static Specification<Account> forFilter(Long userId, AccountFilter filter) {
        return Specification
                .where(ownedBy(userId))
                .and(ofType(filter.type()))
                .and(ofStatus(filter.status()))
                .and(ofCurrency(filter.currencyCode()))
                .and(matchingQuery(filter.q()))
                .and(archiveVisibility(filter.includeArchived()));
    }

    private static Specification<Account> ownedBy(Long userId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("userId"), userId),
                root.get("deletedAt").isNull());
    }

    private static Specification<Account> ofType(AccountType type) {
        return (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type);
    }

    private static Specification<Account> ofStatus(AccountStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    private static Specification<Account> ofCurrency(String currencyCode) {
        return (root, query, cb) -> currencyCode == null || currencyCode.isBlank()
                ? null
                : cb.equal(root.get("currencyCode").as(String.class),
                        currencyCode.trim().toUpperCase(Locale.ROOT));
    }

    private static Specification<Account> matchingQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String like = "%" + escapeLike(q.trim()).toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("institution")), like, '\\'));
        };
    }

    private static Specification<Account> archiveVisibility(Boolean includeArchived) {
        return (root, query, cb) -> Boolean.TRUE.equals(includeArchived)
                ? null
                : cb.notEqual(root.get("status"), AccountStatus.ARCHIVED);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}