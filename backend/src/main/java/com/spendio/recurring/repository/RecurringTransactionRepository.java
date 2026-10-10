package com.spendio.recurring.repository;

import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository
        extends JpaRepository<RecurringTransaction, Long>, JpaSpecificationExecutor<RecurringTransaction> {

    Optional<RecurringTransaction> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<RecurringTransaction> findByIdAndDeletedAtIsNull(Long id);

    /** State transitions (pause/resume/cancel/update) serialise on the rule row itself. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r "
            + "where r.id = :id and r.userId = :userId and r.deletedAt is null")
    Optional<RecurringTransaction> findOwnedForUpdate(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * Execution-side lock (no caller yet knows the user): the scheduler and resume
     * catch-up serialise on the rule row so schedule advancement can never interleave.
     * Lock order everywhere is rule row, then run row - matching {@code findForUpdate}
     * on {@link com.spendio.recurring.repository.RecurringRunRepository}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringTransaction r where r.id = :id and r.deletedAt is null")
    Optional<RecurringTransaction> findByIdForUpdate(@Param("id") Long id);

    /**
     * Scheduler claim batch (ARCHITECTURE.md J.5 step 1): rows are locked only for the
     * duration of the claim transaction, then released - the {@code uk_run} unique key,
     * not the lock, is what guarantees each occurrence posts once across nodes.
     */
    @Query(value = "SELECT * FROM recurring_transactions "
            + "WHERE status = 'ACTIVE' AND deleted_at IS NULL AND next_run_date <= :today "
            + "ORDER BY next_run_date, id LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<RecurringTransaction> claimDue(@Param("today") LocalDate today, @Param("batchSize") int batchSize);

    /** Phase 11 due-soon reminders: active rules whose next run falls inside a window. */
    List<RecurringTransaction> findByStatusAndNextRunDateBetween(RecurringStatus status, LocalDate from,
            LocalDate to);
}
