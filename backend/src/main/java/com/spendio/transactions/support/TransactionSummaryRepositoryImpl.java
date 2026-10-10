package com.spendio.transactions.support;

import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.transactions.domain.Transaction;
import com.spendio.transactions.repository.TransactionSummaryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Criteria-API rollups (ARCHITECTURE.md E.6). Money sums cover only INCOME/EXPENSE
 * rows that are not VOID - adjustments and transfers never masquerade as cash flow;
 * {@code count} stays inclusive so the panel can show the row total alongside.
 */
@Component
public class TransactionSummaryRepositoryImpl implements TransactionSummaryRepository {

    private final EntityManager em;

    public TransactionSummaryRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public Summary summarize(Specification<Transaction> filter) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Transaction> root = query.from(Transaction.class);
        Predicate where = filter == null ? cb.conjunction() : filter.toPredicate(root, query, cb);
        query.multiselect(
                cb.count(root),
                moneySum(cb, root, TransactionType.INCOME),
                moneySum(cb, root, TransactionType.EXPENSE));
        query.where(where);
        Object[] row = em.createQuery(query).getSingleResult();
        return new Summary((Long) row[0], zero(row[1]), zero(row[2]));
    }

    @Override
    public FilterFacets facets(Long userId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Object[]> boundsQuery = cb.createQuery(Object[].class);
        Root<Transaction> boundsRoot = boundsQuery.from(Transaction.class);
        boundsQuery.select(cb.array(cb.min(boundsRoot.get("occurredOn")), cb.max(boundsRoot.get("occurredOn"))));
        boundsQuery.where(owned(cb, boundsRoot, userId));
        Object[] bounds = em.createQuery(boundsQuery).getSingleResult();
        LocalDate minDate = bounds[0] == null ? null : (LocalDate) bounds[0];
        LocalDate maxDate = bounds[1] == null ? null : (LocalDate) bounds[1];

        CriteriaQuery<Object[]> countsQuery = cb.createQuery(Object[].class);
        Root<Transaction> countsRoot = countsQuery.from(Transaction.class);
        countsQuery.multiselect(countsRoot.get("type"), cb.count(countsRoot));
        countsQuery.where(owned(cb, countsRoot, userId));
        countsQuery.groupBy(countsRoot.get("type"));
        Map<TransactionType, Long> typeCounts = new EnumMap<>(TransactionType.class);
        for (TransactionType type : TransactionType.values()) {
            typeCounts.put(type, 0L);
        }
        for (Object[] row : em.createQuery(countsQuery).getResultList()) {
            typeCounts.put((TransactionType) row[0], (Long) row[1]);
        }

        CriteriaQuery<String> currenciesQuery = cb.createQuery(String.class);
        Root<Transaction> currenciesRoot = currenciesQuery.from(Transaction.class);
        currenciesQuery.select(currenciesRoot.get("currencyCode")).distinct(true);
        currenciesQuery.where(owned(cb, currenciesRoot, userId));
        List<String> currencies = new ArrayList<>(em.createQuery(currenciesQuery).getResultList());
        currencies.sort(String::compareToIgnoreCase);

        return new FilterFacets(minDate, maxDate, currencies, typeCounts);
    }

    private Expression<Long> moneySum(CriteriaBuilder cb, Root<Transaction> root, TransactionType type) {
        Expression<Long> baseAmount = root.get("baseAmountMinor");
        Expression<Long> counted = cb.<Long>selectCase()
                .when(cb.and(cb.equal(root.get("type"), type),
                        cb.notEqual(root.get("status"), TransactionStatus.VOID)),
                        baseAmount)
                .otherwise(0L);
        return cb.coalesce(cb.sum(counted), 0L);
    }

    private Predicate owned(CriteriaBuilder cb, Root<Transaction> root, Long userId) {
        return cb.and(cb.equal(root.get("userId"), userId), cb.isNull(root.get("deletedAt")));
    }

    private static long zero(Object value) {
        return value == null ? 0L : (Long) value;
    }
}
