package com.spendio.transactions.repository;

import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.transactions.domain.Transaction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Ledger access (ARCHITECTURE.md B.3): every query is scoped by {@code userId} and
 * excludes soft deletes in the derived names.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    boolean existsByUserIdAndDedupeKeyAndDeletedAtIsNull(Long userId, String dedupeKey);

    Optional<Transaction> findFirstByUserIdAndDedupeKeyAndDeletedAtIsNullOrderByIdDesc(Long userId, String dedupeKey);

    /** Detaches every ledger row from a deleted recurring rule (ARCHITECTURE.md J.8). */
    @Modifying(clearAutomatically = true)
    @Query("update Transaction t set t.recurringTransactionId = null "
            + "where t.userId = :userId and t.recurringTransactionId = :ruleId and t.deletedAt is null")
    int clearRecurringLinks(@Param("userId") Long userId, @Param("ruleId") Long ruleId);

    /** Every row of one transfer group, newest deletes excluded (reversal legs included). */
    List<Transaction> findByUserIdAndTransferGroupIdAndDeletedAtIsNull(Long userId, String transferGroupId);

    /** The original legs of several transfer groups - mirrored reversal rows excluded. */
    List<Transaction> findByUserIdAndTransferGroupIdInAndDeletedAtIsNullAndReversalOfTransactionIdIsNull(
            Long userId, Collection<String> transferGroupIds);

    /** Cross-ledger duplicate probe: same date, amount and currency (idx_tx_dedupe_probe). */
    List<Transaction> findByUserIdAndOccurredOnAndAmountMinorAndCurrencyCodeAndDeletedAtIsNull(
            Long userId, LocalDate occurredOn, long amountMinor, String currencyCode);

    /**
     * Category rollup for budget progress (ARCHITECTURE.md D.10): posted, non-deleted
     * income/expense rows summed per category in base currency over a window.
     * TRANSFER and ADJUSTMENT rows never appear because {@code types} only carries the
     * two ledger types and every such row is filtered server-side as well.
     */
    @Query("select t.category.id, t.type, sum(t.baseAmountMinor) "
            + "from Transaction t "
            + "where t.userId = :userId and t.occurredOn >= :from and t.occurredOn <= :to "
            + "and t.deletedAt is null and t.status = :status "
            + "and t.type in :types and t.category is not null "
            + "group by t.category.id, t.type")
    List<Object[]> spendByCategory(@Param("userId") Long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("status") TransactionStatus status,
            @Param("types") Collection<TransactionType> types);
}
